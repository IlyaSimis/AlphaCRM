#!/usr/bin/env bash
# Создаёт .env со случайными секретами, если его ещё нет. В существующем .env только дописывает
# пароль начального администратора, если его там нет (.env от прошлой версии).
set -euo pipefail
cd "$(dirname "$0")/.."

random_hex() { od -An -N"$1" -tx1 /dev/urandom | tr -d ' \n'; }

if [[ -f .env ]]; then
  if ! grep -q '^CLINIC_ADMIN_PASSWORD=.\+' .env; then
    umask 077
    # Прежний CLINIC_BOOTSTRAP_PASSWORD больше не используется.
    grep -v -e '^CLINIC_ADMIN_PASSWORD=' -e '^CLINIC_BOOTSTRAP_PASSWORD=' .env > .env.tmp || true
    mv .env.tmp .env
    printf '\nCLINIC_ADMIN_PASSWORD=%s\n' "$(random_hex 10)" >> .env
    echo "В .env добавлен пароль начального администратора (логин admin, после входа смените):"
    grep '^CLINIC_ADMIN_PASSWORD=' .env | cut -d= -f2
  else
    echo ".env уже существует — оставляю как есть."
  fi
  # Токен API Telegram-бота: дописывается в .env от прошлой версии.
  if ! grep -q '^CLINIC_BOT_API_TOKEN=.\+' .env; then
    umask 077
    grep -v -e '^CLINIC_BOT_API_TOKEN=' .env > .env.tmp || true
    mv .env.tmp .env
    printf '\nCLINIC_BOT_API_TOKEN=%s\n' "$(random_hex 32)" >> .env
    echo "В .env добавлен токен API Telegram-бота (CLINIC_BOT_API_TOKEN)."
  fi
  exit 0
fi

umask 077
sed \
  -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(random_hex 24)/" \
  -e "s/^CLINIC_KMS_KEY=.*/CLINIC_KMS_KEY=$(random_hex 32)/" \
  -e "s/^CLINIC_ADMIN_PASSWORD=.*/CLINIC_ADMIN_PASSWORD=$(random_hex 10)/" \
  -e "s/^CLINIC_BOT_API_TOKEN=.*/CLINIC_BOT_API_TOKEN=$(random_hex 32)/" \
  .env.example > .env
echo "Создан .env со случайным паролем БД, ключом шифрования и паролем администратора."
echo "Вход: логин admin, пароль (после первого входа его нужно сменить):"
grep '^CLINIC_ADMIN_PASSWORD=' .env | cut -d= -f2
echo "Сохраните копию .env в надёжном месте."
