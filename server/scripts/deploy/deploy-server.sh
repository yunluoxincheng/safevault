#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/scripts/deploy/.env.prod"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[error] Missing ${ENV_FILE}"
  echo "Copy scripts/deploy/.env.prod.example to .env.prod and fill values."
  exit 1
fi

echo "[deploy] Using compose file: ${COMPOSE_FILE} (unified)"
echo "[deploy] Using env file: ${ENV_FILE}"

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" pull || true
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --remove-orphans
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps

echo "[done] deployment finished"
