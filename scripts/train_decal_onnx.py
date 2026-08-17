#!/usr/bin/env python3
"""데칼 번호 분류 ONNX 모델 학습 스크립트.

assets/db/gunpla.db 의 decal 테이블에 저장된 (pdf, 페이지, x%, y%, 번호) 를 이용해
각 데칼 위치를 PDF 에서 정사각형으로 잘라내고, 그 크롭 이미지를 입력으로
decal_number 를 맞추는 EfficientNet-B0 분류기를 학습한 뒤
assets/onnx/decal.onnx / assets/onnx/labels.json 을 생성한다.

크롭 방식은 추론 시점(common.js 의 captureCrop, admin.js 의 CROP_RADIUS_ONNX_PT)과
동일하게 맞춰져 있다: 클릭 지점 ± radius(pt) 정사각 영역을 output_size 픽셀로 렌더링.
전처리도 OnnxDecalService.kt 와 동일하다: 224x224 리사이즈 + ImageNet 정규화.

필요 패키지:
    pip install pymupdf pillow torch torchvision onnxscript

사용 예:
    python scripts/train_decal_onnx.py --extract-only   # 크롭 이미지만 생성
    python scripts/train_decal_onnx.py --epochs 20
"""

import argparse
import copy
import json
import logging
import random
import sqlite3
import sys
import time
import urllib.parse
from pathlib import Path

import pymupdf
from PIL import Image

log = logging.getLogger("decal")

# 프로젝트 기본 경로 (스크립트 위치 기준)
ROOT = Path(__file__).resolve().parent.parent

# 추론 시점과 동일한 크롭 파라미터 (admin.js: CROP_RADIUS_ONNX_PT / CROP_OUTPUT_ONNX_PX)
DEFAULT_RADIUS_PT = 7.0
DEFAULT_OUTPUT_PX = 224

# OnnxDecalService.kt 와 동일한 정규화 상수
IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]


def format_duration(seconds: float) -> str:
    """초를 "3분 12초" 형태로 바꾼다."""
    seconds = int(seconds)
    return f"{seconds // 60}분 {seconds % 60}초" if seconds >= 60 else f"{seconds}초"


def elapsed(started: float) -> str:
    """started 이후 경과 시간을 사람이 읽는 형태로 돌려준다."""
    return format_duration(time.time() - started)


def load_decals(db_path: Path, min_samples: int):
    """DB에서 (pdf 파일명, 페이지, x%, y%, 라벨, 데칼 id) 목록을 읽는다.

    min_samples 미만인 라벨은 학습에서 제외한다.
    """
    con = sqlite3.connect(db_path)
    rows = con.execute(
        """
        SELECT d.id, m.pdf_path, d.page_number, d.x, d.y, d.decal_number
        FROM decal d
        JOIN manual m ON m.id = d.manual_id
        ORDER BY m.pdf_path, d.page_number
        """
    ).fetchall()
    con.close()

    counts = {}
    for _, _, _, _, _, label in rows:
        counts[label] = counts.get(label, 0) + 1

    kept = [r for r in rows if counts[r[5]] >= min_samples]
    dropped = sorted({r[5] for r in rows if counts[r[5]] < min_samples})
    if dropped:
        log.info("샘플 부족(%d개 미만)으로 제외한 라벨 %d개: %s", min_samples, len(dropped), ", ".join(dropped))
    return kept


def crop_path(cache_dir: Path, label: str, decal_id: int) -> Path:
    """라벨별 디렉터리에 데칼 id 로 저장. 파일명에 쓸 수 없는 문자는 퍼센트 인코딩한다."""
    return cache_dir / urllib.parse.quote(label, safe="") / f"{decal_id}.png"


def extract_crops(rows, uploads_dir: Path, cache_dir: Path, radius_pt: float, output_px: int):
    """데칼 위치마다 PDF를 잘라 PNG로 저장한다. 이미 있는 파일은 건너뛴다."""
    scale = output_px / (radius_pt * 2)
    made = skipped = failed = 0
    doc = None
    current_pdf = None
    # 한 페이지에 데칼이 수십 개씩 몰려 있으므로 디스플레이 리스트를 재사용해 페이지 파싱을 한 번만 한다
    # (rows 는 pdf, 페이지 순으로 정렬되어 들어온다)
    current_page = None
    display_list = None
    page_rect = None

    started = time.time()
    for processed, (decal_id, pdf_name, page_number, x_pct, y_pct, label) in enumerate(rows, start=1):
        if processed % 2000 == 0:
            log.info("크롭 %d/%d (%.0f%%) — 생성 %d개, 경과 %s", processed, len(rows),
                     processed / len(rows) * 100, made, elapsed(started))

        out = crop_path(cache_dir, label, decal_id)
        if out.exists():
            skipped += 1
            continue

        if pdf_name != current_pdf:
            if doc is not None:
                doc.close()
            pdf_file = uploads_dir / pdf_name
            if not pdf_file.exists():
                log.warning("PDF 없음: %s", pdf_file)
                doc, current_pdf = None, pdf_name
                continue
            doc = pymupdf.open(pdf_file)
            current_pdf = pdf_name
        if doc is None:
            failed += 1
            continue

        if not (1 <= page_number <= doc.page_count):
            log.warning("페이지 범위 초과: %s p%d", pdf_name, page_number)
            failed += 1
            continue

        if (pdf_name, page_number) != current_page:
            page = doc[page_number - 1]
            display_list = page.get_displaylist()
            page_rect = page.rect
            current_page = (pdf_name, page_number)

        cx = page_rect.x0 + page_rect.width * (x_pct / 100.0)
        cy = page_rect.y0 + page_rect.height * (y_pct / 100.0)
        # 관심 영역이 페이지 밖으로 나가면 교차 영역만 렌더링하고 나머지는 흰색으로 채운다
        # (pdf.js 는 캔버스 전체를 흰 배경으로 칠하고 그리므로 동일한 결과가 된다)
        clip = pymupdf.Rect(cx - radius_pt, cy - radius_pt, cx + radius_pt, cy + radius_pt) & page_rect
        if clip.is_empty:
            failed += 1
            continue

        pix = display_list.get_pixmap(matrix=pymupdf.Matrix(scale, scale), clip=clip, alpha=False)
        img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)

        canvas = Image.new("RGB", (output_px, output_px), (255, 255, 255))
        canvas.paste(img, (round((clip.x0 - (cx - radius_pt)) * scale), round((clip.y0 - (cy - radius_pt)) * scale)))

        out.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(out)
        made += 1

    if doc is not None:
        doc.close()
    log.info("크롭 완료 — 생성 %d개, 재사용 %d개, 실패 %d개", made, skipped, failed)


def build_samples(rows, cache_dir: Path):
    """(이미지 경로, 라벨) 목록과 라벨 목록을 만든다."""
    found = [(crop_path(cache_dir, r[5], r[0]), r[5]) for r in rows]
    found = [(path, label) for path, label in found if path.exists()]

    labels = sorted({label for _, label in found})
    index = {label: i for i, label in enumerate(labels)}
    return [(path, index[label]) for path, label in found], labels


def split_samples(samples, val_ratio: float, seed: int):
    """라벨별로 동일 비율을 떼어내 검증 세트를 만든다(라벨당 최소 1개)."""
    by_label = {}
    for path, target in samples:
        by_label.setdefault(target, []).append((path, target))

    rng = random.Random(seed)
    train, val = [], []
    for items in by_label.values():
        rng.shuffle(items)
        n_val = max(1, round(len(items) * val_ratio)) if len(items) > 1 else 0
        val.extend(items[:n_val])
        train.extend(items[n_val:])
    rng.shuffle(train)
    return train, val


class CropDataset:
    """(이미지 경로, 라벨 인덱스) 목록을 읽어 학습 텐서를 돌려준다.

    macOS 의 DataLoader 워커는 spawn 방식이라 데이터셋이 피클링 가능해야 하므로 모듈 최상위에 둔다.
    """

    def __init__(self, items, transform):
        self.items = items
        self.transform = transform

    def __len__(self):
        return len(self.items)

    def __getitem__(self, i):
        path, target = self.items[i]
        with Image.open(path) as img:
            return self.transform(img.convert("RGB")), target


def load_checkpoint(path: Path, model, optimizer, scheduler, labels, device):
    """체크포인트가 있으면 이어서 학습할 (다음 에폭, 최고 정확도) 를, 없으면 (1, -1.0) 을 돌려준다."""
    import torch

    if not path.exists():
        return 1, -1.0

    ckpt = torch.load(path, map_location=device, weights_only=False)
    if ckpt["labels"] != labels:
        log.warning("체크포인트의 클래스 구성이 달라 무시하고 처음부터 학습한다: %s", path)
        return 1, -1.0

    model.load_state_dict(ckpt["model"])
    optimizer.load_state_dict(ckpt["optimizer"])
    scheduler.load_state_dict(ckpt["scheduler"])
    log.info("체크포인트에서 이어서 학습: epoch %d부터, best_acc=%.4f", ckpt["epoch"] + 1, ckpt["best_acc"])
    return ckpt["epoch"] + 1, ckpt["best_acc"]


def train(samples, labels, args):
    """EfficientNet-B0 을 학습하고, 검증 정확도 최고 기록을 갱신할 때마다 ONNX 로 내보낸다."""
    import torch
    from torch import nn
    from torch.utils.data import DataLoader
    from torchvision import transforms
    from torchvision.models import EfficientNet_B0_Weights, efficientnet_b0

    torch.manual_seed(args.seed)

    if torch.cuda.is_available():
        device = torch.device("cuda")
    elif torch.backends.mps.is_available():
        device = torch.device("mps")
    else:
        device = torch.device("cpu")
    log.info("device=%s, 샘플 %d개, 클래스 %d개", device, len(samples), len(labels))

    normalize = transforms.Normalize(IMAGENET_MEAN, IMAGENET_STD)
    train_tf = transforms.Compose(
        [
            # 클릭 좌표가 사람 손으로 찍혀 조금씩 흔들리므로 소폭의 이동/회전/확대만 준다
            transforms.RandomAffine(degrees=8, translate=(0.05, 0.05), scale=(0.9, 1.1), fill=255),
            transforms.Resize((args.output_px, args.output_px)),
            transforms.ToTensor(),
            normalize,
        ]
    )
    eval_tf = transforms.Compose(
        [
            transforms.Resize((args.output_px, args.output_px)),
            transforms.ToTensor(),
            normalize,
        ]
    )

    train_items, val_items = split_samples(samples, args.val_ratio, args.seed)
    log.info("학습 %d개 / 검증 %d개", len(train_items), len(val_items))
    train_loader = DataLoader(
        CropDataset(train_items, train_tf),
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=args.workers,
    )
    val_loader = DataLoader(
        CropDataset(val_items, eval_tf),
        batch_size=args.batch_size,
        num_workers=args.workers,
    )

    model = efficientnet_b0(weights=EfficientNet_B0_Weights.IMAGENET1K_V1)
    model.classifier[1] = nn.Linear(model.classifier[1].in_features, len(labels))
    model = model.to(device)

    criterion = nn.CrossEntropyLoss(label_smoothing=0.05)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)
    start_epoch, best_acc = load_checkpoint(args.checkpoint, model, optimizer, scheduler, labels, device)
    # 에폭 하나가 십 분 넘게 걸리므로 배치 진행도를 스무 번 정도 나눠서 남긴다
    log_every = max(1, len(train_loader) // 20)
    for epoch in range(start_epoch, args.epochs + 1):
        started = time.time()
        model.train()
        total_loss = 0.0
        for batch, (images, targets) in enumerate(train_loader, start=1):
            images, targets = images.to(device), targets.to(device)
            optimizer.zero_grad()
            loss = criterion(model(images), targets)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * images.size(0)

            if batch % log_every == 0:
                done = batch * args.batch_size
                log.info(
                    "epoch %d/%d — 배치 %d/%d (%.0f%%) loss=%.4f, 경과 %s, 이 에폭 남은 시간 약 %s",
                    epoch, args.epochs, batch, len(train_loader), batch / len(train_loader) * 100,
                    total_loss / done, elapsed(started),
                    format_duration((time.time() - started) * (len(train_loader) / batch - 1)),
                )
        scheduler.step()

        log.info("epoch %d/%d — 검증 중 (%d개)", epoch, args.epochs, len(val_items))
        model.eval()
        correct = 0
        with torch.no_grad():
            for images, targets in val_loader:
                images, targets = images.to(device), targets.to(device)
                correct += (model(images).argmax(1) == targets).sum().item()
        acc = correct / len(val_items) if val_items else 0.0
        log.info(
            "epoch %d/%d 완료 — loss=%.4f val_acc=%.4f (%s)",
            epoch, args.epochs, total_loss / len(train_items), acc, elapsed(started),
        )

        # 최고 기록을 갱신할 때마다 바로 내보낸다 — 학습이 중간에 끊겨도 그 시점의 최고 모델은 남는다
        if acc > best_acc:
            best_acc = acc
            export_onnx(copy.deepcopy(model), labels, args)

        torch.save(
            {
                "epoch": epoch,
                "best_acc": best_acc,
                "labels": labels,
                "model": model.state_dict(),
                "optimizer": optimizer.state_dict(),
                "scheduler": scheduler.state_dict(),
            },
            args.checkpoint,
        )

    log.info("학습 완료 — 최고 검증 정확도 %.4f", best_acc)


def export_onnx(model, labels, args):
    """Kotlin(OnnxDecalService)이 기대하는 형태 — 입력 이름 input, 출력은 로짓 — 로 내보낸다."""
    import torch

    args.model_out.parent.mkdir(parents=True, exist_ok=True)
    model = model.cpu().eval()
    dummy = torch.zeros(1, 3, args.output_px, args.output_px)
    torch.onnx.export(
        model,
        dummy,
        str(args.model_out),
        input_names=["input"],
        output_names=["output"],
        opset_version=18,
        # 기본값(True)이면 가중치가 decal.onnx.data 로 분리돼 파일 두 개를 같이 배포해야 한다.
        # 서버 설정(app.onnx.model)은 .onnx 경로 하나만 받으므로 단일 파일로 내보낸다.
        external_data=False,
    )
    args.labels_out.parent.mkdir(parents=True, exist_ok=True)
    args.labels_out.write_text(json.dumps(labels, ensure_ascii=False), encoding="utf-8")
    log.info("내보내기 완료 — %s, %s (%d개 클래스)", args.model_out, args.labels_out, len(labels))


def parse_args():
    p = argparse.ArgumentParser(description="데칼 번호 분류 ONNX 모델 학습")
    p.add_argument("--db", type=Path, default=ROOT / "assets/db/gunpla.db")
    p.add_argument("--uploads", type=Path, default=ROOT / "assets/uploads")
    p.add_argument("--cache", type=Path, default=ROOT / "assets/onnx/crops", help="크롭 이미지 저장 위치")
    p.add_argument("--model-out", type=Path, default=ROOT / "assets/onnx/decal.onnx")
    p.add_argument("--labels-out", type=Path, default=ROOT / "assets/onnx/labels.json")
    p.add_argument(
        "--checkpoint",
        type=Path,
        default=ROOT / "assets/onnx/checkpoint.pt",
        help="에폭마다 저장하며, 파일이 있으면 그 다음 에폭부터 이어서 학습한다 (처음부터 하려면 삭제)",
    )
    p.add_argument(
        "--radius-pt",
        type=float,
        default=DEFAULT_RADIUS_PT,
        help=f"크롭 반경(pt). 추론 시점과 반드시 같아야 한다 (기본 {DEFAULT_RADIUS_PT})",
    )
    p.add_argument("--output-px", type=int, default=DEFAULT_OUTPUT_PX, help="크롭 출력 및 모델 입력 크기")
    p.add_argument("--min-samples", type=int, default=10, help="이 개수 미만인 라벨은 학습에서 제외")
    p.add_argument("--val-ratio", type=float, default=0.15)
    p.add_argument("--epochs", type=int, default=15)
    p.add_argument("--batch-size", type=int, default=32)
    p.add_argument("--lr", type=float, default=3e-4)
    p.add_argument("--workers", type=int, default=4)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--extract-only", action="store_true", help="크롭 이미지만 만들고 종료")
    return p.parse_args()


def main():
    args = parse_args()
    # 루트는 WARNING 으로 두어 onnxscript 등 외부 라이브러리의 INFO 로그는 걸러낸다
    logging.basicConfig(level=logging.WARNING, format="%(asctime)s %(levelname)s %(message)s", datefmt="%H:%M:%S")
    log.setLevel(logging.INFO)

    rows = load_decals(args.db, args.min_samples)
    log.info("대상 데칼 %d개", len(rows))

    extract_crops(rows, args.uploads, args.cache, args.radius_pt, args.output_px)
    if args.extract_only:
        return

    samples, labels = build_samples(rows, args.cache)
    if not samples:
        log.error("크롭 이미지가 없습니다.")
        sys.exit(1)

    train(samples, labels, args)


if __name__ == "__main__":
    main()
