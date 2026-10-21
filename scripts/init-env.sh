#!/usr/bin/env bash
# Создаёт .env со случайными секретами, если его ещё нет. Существующий .env не трогает.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  echo ".env уже существует — оставляю как есть."
  exit 0
fi

random_hex() { od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'; }

umask 077
sed \
  -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(random_hex 24)/" \
  -e "s/^CLINIC_KMS_KEY=.*/CLINIC_KMS_KEY=$(random_hex 32)/" \
  .env.example > .env
echo "Создан .env со случайным паролем БД и ключом шифрования. Сохраните его копию в надёжном месте."
