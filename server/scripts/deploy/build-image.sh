#!/usr/bin/env bash
set -euo pipefail

IMAGE_NAME="${1:-safevault-backend}"
IMAGE_TAG="${2:-latest}"

echo "[build] image=${IMAGE_NAME}:${IMAGE_TAG}"
docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" .
echo "[done] built ${IMAGE_NAME}:${IMAGE_TAG}"
