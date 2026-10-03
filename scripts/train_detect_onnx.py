#!/usr/bin/env python3
"""데칼 위치 탐지(히트맵) ONNX 모델 학습 스크립트.

assets/db/gunpla.db 의 decal 테이블에 저장된 (pdf, 페이지, x%, y%) 를 정답 점으로 삼아
페이지 이미지를 넣으면 "데칼 표시 중심일 확률" 히트맵을 내는 모델을 학습한 뒤
assets/onnx/detect.onnx 를 생성한다. 번호는 이 모델이 아니라 decal.onnx(분류기)가 읽는다.

- 페이지는 짧은 변이 PAGE_SHORT_SIDE_PX 가 되도록 렌더링한다. PDF 마다 pt 크기가 제각각이라
  (긴 변 1000pt, 2900pt, 펼침면 2500x727pt 등) 짧은 변을 기준으로 맞춰야 마커 크기가 비슷해진다.
  추론 시점(admin.js 의 capturePage, DETECT_SHORT_SIDE_PX)과 반드시 같아야 한다.
- 모델: ResNet18(ImageNet) + FPN, 출력은 입력의 1/2 해상도 1채널 히트맵(시그모이드 포함).
- 전처리는 OnnxDetectService.kt 와 동일하다: ImageNet 정규화, 32의 배수가 되도록 오른쪽·아래를 흰색으로 채움.

등록된 페이지에서는 모든 데칼이 빠짐없이 찍혀 있다고 가정한다(찍히지 않은 곳은 "데칼 아님"으로 학습된다).

필요 패키지:
    pip install pymupdf pillow torch torchvision onnxscript

사용 예:
    python scripts/train_detect_onnx.py --extract-only   # 페이지 이미지만 생성
    python scripts/train_detect_onnx.py --epochs 30
"""

import argparse
import copy
import logging
import random
import sqlite3
import sys
import time
from pathlib import Path

import pymupdf
from PIL import Image

log = logging.getLogger("detect")

ROOT = Path(__file__).resolve().parent.parent

# 추론 시점과 동일한 렌더링 크기 (admin.js: DETECT_SHORT_SIDE_PX)
PAGE_SHORT_SIDE_PX = 1536
# 히트맵 해상도 = 입력 / OUTPUT_STRIDE
OUTPUT_STRIDE = 2

IMAGENET_MEAN = [0.485, 0.456, 0.406]
IMAGENET_STD = [0.229, 0.224, 0.225]


def format_duration(seconds: float) -> str:
    seconds = int(seconds)
    return f"{seconds // 60}분 {seconds % 60}초" if seconds >= 60 else f"{seconds}초"


def elapsed(started: float) -> str:
    return format_duration(time.time() - started)


def load_pages(db_path: Path):
    """데칼이 하나 이상 등록된 페이지마다 (pdf 파일명, 페이지, [(x%, y%), ...]) 를 돌려준다."""
    con = sqlite3.connect(db_path)
    rows = con.execute(
        """
        SELECT m.pdf_path, d.page_number, d.x, d.y
        FROM decal d
        JOIN manual m ON m.id = d.manual_id
        ORDER BY m.pdf_path, d.page_number
        """
    ).fetchall()
    con.close()

    pages = {}
    for pdf_name, page_number, x, y in rows:
        pages.setdefault((pdf_name, page_number), []).append((x, y))
    return [(pdf_name, page_number, points) for (pdf_name, page_number), points in pages.items()]


def page_path(cache_dir: Path, pdf_name: str, page_number: int) -> Path:
    return cache_dir / f"{Path(pdf_name).stem}.{page_number:03d}.png"


def extract_pages(pages, uploads_dir: Path, cache_dir: Path, short_side: int):
    """페이지를 PNG 로 렌더링해 저장한다. 이미 있는 파일은 건너뛴다."""
    cache_dir.mkdir(parents=True, exist_ok=True)
    made = skipped = failed = 0
    doc = None
    current_pdf = None
    started = time.time()
    for processed, (pdf_name, page_number, _) in enumerate(pages, start=1):
        if processed % 100 == 0:
            log.info("렌더링 %d/%d — 생성 %d개, 경과 %s", processed, len(pages), made, elapsed(started))

        out = page_path(cache_dir, pdf_name, page_number)
        if out.exists():
            skipped += 1
            continue

        if pdf_name != current_pdf:
            if doc is not None:
                doc.close()
            pdf_file = uploads_dir / pdf_name
            doc = pymupdf.open(pdf_file) if pdf_file.exists() else None
            current_pdf = pdf_name
            if doc is None:
                log.warning("PDF 없음: %s", pdf_file)
        if doc is None or not (1 <= page_number <= doc.page_count):
            failed += 1
            continue

        page = doc[page_number - 1]
        s = short_side / min(page.rect.width, page.rect.height)
        pix = page.get_pixmap(matrix=pymupdf.Matrix(s, s), alpha=False)
        pix.save(out)
        made += 1

    if doc is not None:
        doc.close()
    log.info("렌더링 완료 — 생성 %d개, 재사용 %d개, 실패 %d개", made, skipped, failed)


def build_samples(pages, cache_dir: Path):
    """(페이지 이미지 경로, [(x%, y%), ...]) 목록."""
    found = [(page_path(cache_dir, pdf_name, page_number), points) for pdf_name, page_number, points in pages]
    return [(path, points) for path, points in found if path.exists()]


class PageCropDataset:
    """페이지 이미지에서 crop_px 정사각 영역을 잘라 (이미지 텐서, 정답 히트맵) 을 돌려준다.

    학습 시에는 데칼 주변 위주로 무작위 위치·배율을 고르고, 검증 시에는 페이지별로 고정된 위치를 쓴다.
    macOS 의 DataLoader 워커는 spawn 방식이라 피클링 가능하도록 모듈 최상위에 둔다.
    """

    def __init__(self, samples, crop_px: int, crops_per_page: int, train: bool, sigma: float):
        self.samples = samples
        self.crop_px = crop_px
        self.crops_per_page = crops_per_page
        self.train = train
        self.sigma = sigma

    def __len__(self):
        return len(self.samples) * self.crops_per_page

    def __getitem__(self, i):
        import torch
        import torchvision.transforms.functional as F

        path, points = self.samples[i // self.crops_per_page]
        rng = random.Random() if self.train else random.Random(i)

        with Image.open(path) as img:
            img = img.convert("RGB")
            # 마커 크기가 PDF 마다 조금씩 달라서 배율을 흔들어 준다
            scale = rng.uniform(0.8, 1.25) if self.train else 1.0
            if scale != 1.0:
                img = img.resize((round(img.width * scale), round(img.height * scale)), Image.BILINEAR)
            w, h = img.size
            pts = [(x / 100 * w, y / 100 * h) for x, y in points]

            c = self.crop_px
            if pts and rng.random() < 0.7:
                # 데칼 하나를 골라 그 주변이 들어오도록 자른다
                px, py = rng.choice(pts)
                left = px - rng.uniform(0.1, 0.9) * c
                top = py - rng.uniform(0.1, 0.9) * c
            else:
                left = rng.uniform(0, max(0, w - c))
                top = rng.uniform(0, max(0, h - c))
            left, top = round(left), round(top)
            # 페이지 밖은 흰색 (pdf.js 도 흰 배경으로 렌더링한다)
            canvas = Image.new("RGB", (c, c), (255, 255, 255))
            canvas.paste(img.crop((left, top, left + c, top + c)), (0, 0))

        tensor = F.normalize(F.to_tensor(canvas), IMAGENET_MEAN, IMAGENET_STD)

        hs = c // OUTPUT_STRIDE
        heatmap = torch.zeros(hs, hs)
        ys, xs = torch.meshgrid(torch.arange(hs, dtype=torch.float32), torch.arange(hs, dtype=torch.float32), indexing="ij")
        for px, py in pts:
            # 히트맵 셀 (i, j) 의 중심은 입력 좌표 (j + 0.5) * stride
            cx = (px - left) / OUTPUT_STRIDE - 0.5
            cy = (py - top) / OUTPUT_STRIDE - 0.5
            if -3 * self.sigma <= cx < hs + 3 * self.sigma and -3 * self.sigma <= cy < hs + 3 * self.sigma:
                g = torch.exp(-((xs - cx) ** 2 + (ys - cy) ** 2) / (2 * self.sigma**2))
                heatmap = torch.maximum(heatmap, g)
            # 정확히 중심 셀은 1 로 만들어 focal loss 의 양성으로 잡히게 한다
            ix, iy = round(cx), round(cy)
            if 0 <= ix < hs and 0 <= iy < hs:
                heatmap[iy, ix] = 1.0
        return tensor, heatmap.unsqueeze(0)


def build_model():
    """ResNet18 백본 + FPN 디코더. 출력은 입력의 1/2 해상도 1채널 확률 히트맵."""
    import torch
    from torch import nn
    from torchvision.models import ResNet18_Weights, resnet18

    class DetectNet(nn.Module):
        def __init__(self):
            super().__init__()
            r = resnet18(weights=ResNet18_Weights.IMAGENET1K_V1)
            self.stem = nn.Sequential(r.conv1, r.bn1, r.relu)  # 1/2, 64
            self.pool = r.maxpool
            self.layer1 = r.layer1  # 1/4, 64
            self.layer2 = r.layer2  # 1/8, 128
            self.layer3 = r.layer3  # 1/16, 256
            ch = 64
            self.lat3 = nn.Conv2d(256, ch, 1)
            self.lat2 = nn.Conv2d(128, ch, 1)
            self.lat1 = nn.Conv2d(64, ch, 1)
            self.lat0 = nn.Conv2d(64, ch, 1)
            self.head = nn.Sequential(
                nn.Conv2d(ch, ch, 3, padding=1),
                nn.ReLU(inplace=True),
                nn.Conv2d(ch, 1, 1),
            )
            # 초기 출력이 거의 0 이 되도록 해 학습 초반 손실이 폭주하지 않게 한다 (CenterNet 방식)
            nn.init.constant_(self.head[-1].bias, -2.19)

        def forward(self, x):
            up = nn.functional.interpolate
            c0 = self.stem(x)
            c1 = self.layer1(self.pool(c0))
            c2 = self.layer2(c1)
            c3 = self.layer3(c2)
            p = self.lat3(c3)
            p = up(p, scale_factor=2, mode="nearest") + self.lat2(c2)
            p = up(p, scale_factor=2, mode="nearest") + self.lat1(c1)
            p = up(p, scale_factor=2, mode="nearest") + self.lat0(c0)
            return self.head(p)

        def predict(self, x):
            return torch.sigmoid(self.forward(x))

    return DetectNet()


def focal_loss(logits, target):
    """CenterNet 의 페널티 감소 focal loss. 양성(중심 셀) 개수로 정규화한다."""
    import torch

    pred = torch.sigmoid(logits).clamp(1e-4, 1 - 1e-4)
    pos = target.eq(1).float()
    neg = 1 - pos
    pos_loss = torch.log(pred) * (1 - pred) ** 2 * pos
    neg_loss = torch.log(1 - pred) * pred**2 * (1 - target) ** 4 * neg
    num_pos = pos.sum()
    return -(pos_loss.sum() + neg_loss.sum()) / num_pos.clamp(min=1)


def find_peaks(heatmap, threshold: float):
    """[N,1,H,W] 확률 히트맵에서 3x3 지역 최댓값이면서 threshold 이상인 셀 목록을 배치별로 돌려준다."""
    import torch

    hmax = torch.nn.functional.max_pool2d(heatmap, 3, stride=1, padding=1)
    keep = (heatmap == hmax) & (heatmap >= threshold)
    return [torch.nonzero(keep[b, 0]).tolist() for b in range(heatmap.size(0))]


def count_matches(pred_peaks, target, radius: float):
    """예측 피크와 정답 중심 셀을 radius(히트맵 셀) 이내로 1:1 매칭해 (TP, 예측 수, 정답 수) 를 센다."""
    import torch

    tp = n_pred = n_true = 0
    for b, peaks in enumerate(pred_peaks):
        truth = torch.nonzero(target[b, 0].eq(1)).tolist()
        n_pred += len(peaks)
        n_true += len(truth)
        used = set()
        for py, px in peaks:
            best, best_d = None, radius
            for k, (ty, tx) in enumerate(truth):
                d = ((py - ty) ** 2 + (px - tx) ** 2) ** 0.5
                if k not in used and d <= best_d:
                    best, best_d = k, d
            if best is not None:
                used.add(best)
                tp += 1
    return tp, n_pred, n_true


def split_samples(samples, val_ratio: float, seed: int):
    """페이지 단위로 검증 세트를 떼어낸다."""
    items = list(samples)
    random.Random(seed).shuffle(items)
    n_val = max(1, round(len(items) * val_ratio))
    return items[n_val:], items[:n_val]


def load_checkpoint(path: Path, model, optimizer, scheduler, device):
    """체크포인트가 있으면 (다음 에폭, 최고 F1) 을, 없으면 (1, -1.0) 을 돌려준다."""
    import torch

    if not path.exists():
        return 1, -1.0
    ckpt = torch.load(path, map_location=device, weights_only=False)
    model.load_state_dict(ckpt["model"])
    optimizer.load_state_dict(ckpt["optimizer"])
    scheduler.load_state_dict(ckpt["scheduler"])
    log.info("체크포인트에서 이어서 학습: epoch %d부터, best_f1=%.4f", ckpt["epoch"] + 1, ckpt["best_f1"])
    return ckpt["epoch"] + 1, ckpt["best_f1"]


def train(samples, args):
    import torch
    from torch.utils.data import DataLoader

    torch.manual_seed(args.seed)
    if torch.cuda.is_available():
        device = torch.device("cuda")
    elif torch.backends.mps.is_available():
        device = torch.device("mps")
    else:
        device = torch.device("cpu")

    train_items, val_items = split_samples(samples, args.val_ratio, args.seed)
    log.info("device=%s, 학습 페이지 %d개 / 검증 페이지 %d개", device, len(train_items), len(val_items))

    train_loader = DataLoader(
        PageCropDataset(train_items, args.crop_px, args.crops_per_page, True, args.sigma),
        batch_size=args.batch_size,
        shuffle=True,
        num_workers=args.workers,
        persistent_workers=args.workers > 0,
    )
    val_loader = DataLoader(
        PageCropDataset(val_items, args.crop_px, 4, False, args.sigma),
        batch_size=args.batch_size,
        num_workers=args.workers,
    )

    model = build_model().to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    args.checkpoint.parent.mkdir(parents=True, exist_ok=True)
    start_epoch, best_f1 = load_checkpoint(args.checkpoint, model, optimizer, scheduler, device)
    log_every = max(1, len(train_loader) // 10)
    for epoch in range(start_epoch, args.epochs + 1):
        started = time.time()
        model.train()
        total_loss = 0.0
        for batch, (images, targets) in enumerate(train_loader, start=1):
            images, targets = images.to(device), targets.to(device)
            optimizer.zero_grad()
            loss = focal_loss(model(images), targets)
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
            if batch % log_every == 0:
                log.info(
                    "epoch %d/%d — 배치 %d/%d loss=%.4f, 경과 %s",
                    epoch, args.epochs, batch, len(train_loader), total_loss / batch, elapsed(started),
                )
        scheduler.step()

        model.eval()
        tp = n_pred = n_true = 0
        with torch.no_grad():
            for images, targets in val_loader:
                heatmap = model.predict(images.to(device)).cpu()
                t, p, n = count_matches(find_peaks(heatmap, args.threshold), targets, args.match_radius)
                tp, n_pred, n_true = tp + t, n_pred + p, n_true + n
        precision = tp / n_pred if n_pred else 0.0
        recall = tp / n_true if n_true else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        log.info(
            "epoch %d/%d 완료 — loss=%.4f precision=%.4f recall=%.4f f1=%.4f (%s)",
            epoch, args.epochs, total_loss / len(train_loader), precision, recall, f1, elapsed(started),
        )

        if f1 > best_f1:
            best_f1 = f1
            export_onnx(copy.deepcopy(model), args)

        torch.save(
            {
                "epoch": epoch,
                "best_f1": best_f1,
                "model": model.state_dict(),
                "optimizer": optimizer.state_dict(),
                "scheduler": scheduler.state_dict(),
            },
            args.checkpoint,
        )

    log.info("학습 완료 — 최고 검증 F1 %.4f", best_f1)


def export_onnx(model, args):
    """Kotlin(OnnxDetectService)이 기대하는 형태로 내보낸다.

    입력 input [1,3,H,W] (H, W 는 32의 배수, 가변), 출력 heatmap [1,1,H/2,W/2] (시그모이드 적용된 확률).
    """
    import torch

    class Exported(torch.nn.Module):
        def __init__(self, net):
            super().__init__()
            self.net = net

        def forward(self, x):
            return torch.sigmoid(self.net(x))

    args.model_out.parent.mkdir(parents=True, exist_ok=True)
    exported = Exported(model.cpu().eval()).eval()
    dummy = torch.zeros(1, 3, 512, 768)
    torch.onnx.export(
        exported,
        dummy,
        str(args.model_out),
        input_names=["input"],
        output_names=["heatmap"],
        dynamic_axes={"input": {2: "height", 3: "width"}, "heatmap": {2: "out_height", 3: "out_width"}},
        opset_version=18,
        external_data=False,
    )
    log.info("내보내기 완료 — %s", args.model_out)


def parse_args():
    p = argparse.ArgumentParser(description="데칼 위치 탐지(히트맵) ONNX 모델 학습")
    p.add_argument("--db", type=Path, default=ROOT / "assets/db/gunpla.db")
    p.add_argument("--uploads", type=Path, default=ROOT / "assets/uploads")
    p.add_argument("--cache", type=Path, default=ROOT / "assets/onnx/pages", help="페이지 이미지 저장 위치")
    p.add_argument("--model-out", type=Path, default=ROOT / "assets/onnx/detect.onnx")
    p.add_argument(
        "--checkpoint",
        type=Path,
        default=ROOT / "assets/onnx/detect-checkpoint.pt",
        help="에폭마다 저장하며, 파일이 있으면 그 다음 에폭부터 이어서 학습한다 (처음부터 하려면 삭제)",
    )
    p.add_argument(
        "--short-side",
        type=int,
        default=PAGE_SHORT_SIDE_PX,
        help=f"페이지 렌더링 짧은 변(px). 추론 시점과 반드시 같아야 한다 (기본 {PAGE_SHORT_SIDE_PX})",
    )
    p.add_argument("--crop-px", type=int, default=512, help="학습 시 잘라내는 정사각 영역 크기 (32의 배수)")
    p.add_argument("--crops-per-page", type=int, default=8, help="한 에폭에서 페이지당 잘라내는 횟수")
    p.add_argument("--sigma", type=float, default=2.0, help="정답 가우시안 반경(히트맵 셀)")
    p.add_argument("--threshold", type=float, default=0.3, help="검증 시 피크로 인정하는 최소 확률")
    p.add_argument("--match-radius", type=float, default=4.0, help="검증 시 정답과 같은 점으로 보는 거리(히트맵 셀)")
    p.add_argument("--val-ratio", type=float, default=0.1)
    p.add_argument("--epochs", type=int, default=30)
    p.add_argument("--batch-size", type=int, default=16)
    p.add_argument("--lr", type=float, default=5e-4)
    p.add_argument("--workers", type=int, default=4)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--extract-only", action="store_true", help="페이지 이미지만 만들고 종료")
    return p.parse_args()


def main():
    args = parse_args()
    logging.basicConfig(level=logging.WARNING, format="%(asctime)s %(levelname)s %(message)s", datefmt="%H:%M:%S")
    log.setLevel(logging.INFO)

    pages = load_pages(args.db)
    log.info("대상 페이지 %d개, 데칼 %d개", len(pages), sum(len(p[2]) for p in pages))

    extract_pages(pages, args.uploads, args.cache, args.short_side)
    if args.extract_only:
        return

    samples = build_samples(pages, args.cache)
    if not samples:
        log.error("페이지 이미지가 없습니다.")
        sys.exit(1)
    train(samples, args)


if __name__ == "__main__":
    main()
