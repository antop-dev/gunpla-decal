#!/usr/bin/env bash
#
# 데칼 번호 분류 ONNX 모델 학습 실행 스크립트.
#
# 처음 실행하면 scripts/.venv 가상환경을 만들고 필요한 패키지를 설치한다.
# 그 뒤로는 기존 가상환경을 그대로 쓴다. 인자는 train_decal_onnx.py 에 그대로 전달된다.
#
#   ./scripts/train_decal_onnx.sh                    # 크롭 생성 + 15 에폭 학습
#   ./scripts/train_decal_onnx.sh --epochs 20
#   ./scripts/train_decal_onnx.sh --extract-only     # 크롭 이미지만 생성
#
# 학습은 assets/onnx/checkpoint.pt 에 에폭마다 저장되고, 파일이 있으면 그 다음 에폭부터
# 이어서 학습한다. 처음부터 다시 하려면 이 파일을 지운다.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$SCRIPT_DIR/.venv"

if [ ! -d "$VENV" ]; then
    echo "가상환경 생성: $VENV"
    python3 -m venv "$VENV"
    "$VENV/bin/pip" install --quiet --upgrade pip
    echo "패키지 설치 중 (torch 포함, 수 분 걸립니다)"
    "$VENV/bin/pip" install --quiet pymupdf pillow torch torchvision onnxscript
fi

exec "$VENV/bin/python" -u "$SCRIPT_DIR/train_decal_onnx.py" "$@"
