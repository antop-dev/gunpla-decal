#!/usr/bin/env bash
#
# 데칼 위치 탐지(히트맵) ONNX 모델 학습 실행 스크립트.
#
# 가상환경(scripts/.venv)은 train_decal_onnx.sh 와 같이 쓴다. 인자는 train_detect_onnx.py 에 그대로 전달된다.
#
#   ./scripts/train_detect_onnx.sh                    # 페이지 렌더링 + 30 에폭 학습
#   ./scripts/train_detect_onnx.sh --epochs 50
#   ./scripts/train_detect_onnx.sh --extract-only     # 페이지 이미지만 생성
#
# 학습은 assets/onnx/detect-checkpoint.pt 에 에폭마다 저장되고, 파일이 있으면 그 다음 에폭부터
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

exec "$VENV/bin/python" -u "$SCRIPT_DIR/train_detect_onnx.py" "$@"
