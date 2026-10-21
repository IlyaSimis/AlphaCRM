#!/usr/bin/env bash
# Одна команда: собрать образы и поднять PostgreSQL, backend и интерфейс.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v docker >/dev/null || { echo "Docker не найден. Установите Docker Desktop и включите интеграцию с WSL."; exit 1; }
had_env=1; [[ -f .env ]] || had_env=0
volume_exists=0
docker volume inspect quiet-clinic_quiet-clinic-postgres >/dev/null 2>&1 && volume_exists=1
./scripts/init-env.sh
if [[ $had_env == 0 && $volume_exists == 1 ]]; then
  cat <<'MSG'

Найдена база от прошлых запусков (том quiet-clinic_quiet-clinic-postgres).
Её пароль и ключ шифрования не совпадают с только что созданным .env, backend не подключится.
  • Данные не нужны:  docker compose down -v   и снова ./scripts/start.sh
  • Данные нужны:     впишите в .env старые POSTGRES_PASSWORD и CLINIC_KMS_KEY
MSG
  exit 1
fi

echo "Сборка и запуск (первый раз — несколько минут)..."
if ! docker compose up -d --build --wait --wait-timeout 300; then
  echo
  echo "Запуск не удался. Последние строки логов backend:"
  docker compose ps -a
  docker compose logs --no-color --tail 80 backend
  exit 1
fi

port=$(grep -E '^APP_PORT=' .env | cut -d= -f2 || true)
echo
echo "Готово: http://localhost:${port:-8080}"
echo "Логи:      docker compose logs -f backend"
echo "Остановка: ./scripts/stop.sh"
