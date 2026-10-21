#!/usr/bin/env bash
# Останавливает контейнеры. Данные БД сохраняются в томе quiet-clinic-postgres.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose down
