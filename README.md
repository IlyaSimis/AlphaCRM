# Quiet Clinic — full-stack MVP

Запускаемый вертикальный контур внутренней системы амбулаторной клиники: React/MUI frontend и модульный Spring backend. Все seed-данные синтетические. Проект не заявляет соответствие законодательству или регуляторным требованиям.

## Архитектура

- Maven reactor с backend-приложением `backend/app`;
- Spring Boot 4.1.1, Spring Modulith 2.1.1, JPA, Liquibase, PostgreSQL 18;
- модули: patients, scheduling, clinical/documents, billing, communications/outbox, audit и platform;
- запрет пересекающихся активных записей: проверка приложения плюс PostgreSQL exclusion constraint;
- клинические тексты и snapshot документа шифруются AES-256-GCM через заменяемый `KmsPort`; dev-адаптер использует только локальный ключ;
- `SIGNED_LOCKED` защищён доменным инвариантом и PostgreSQL trigger; исправление — новый связанный документ;
- payment/notification интеграции представлены портами и безопасными mock-адаптерами;
- audit фиксирует чтения и изменения без клинического текста;
- локальный auth fallback: заголовки `X-Demo-Role` и `X-Actor-Id`. Это только dev-механизм, не production IAM.

Окружение агента содержит Java 21 LTS, но не Java 25/Docker. Поэтому build зафиксирован на ближайшей совместимой Java 21; Spring Boot 4.1.1 и Modulith 2.1.1 остаются целевыми стабильными линиями. Для production следует переключить Maven toolchain/runtime на Java 25 после появления JDK 25.

## Windows + WSL: prerequisites

В PowerShell от имени администратора:

```powershell
wsl --install -d Ubuntu-24.04
wsl --update
```

После перезагрузки откройте Ubuntu и установите Git, Java 21 LTS и базовые утилиты:

```bash
sudo apt update
sudo apt install -y git curl unzip openssl openjdk-21-jdk
java -version
```

Проект собирается на Java 21. Глобальный Maven не нужен: `./mvnw` скачает закреплённый Maven 3.9.11 в пользовательский кэш.

Установите Node.js 22 через nvm:

```bash
curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh -o /tmp/install-nvm.sh
bash /tmp/install-nvm.sh
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
nvm install 22
nvm use 22
node --version
npm --version
```

Для PostgreSQL-варианта установите Docker Desktop для Windows, включите **Use the WSL 2 based engine**, затем в **Settings → Resources → WSL Integration** разрешите интеграцию для Ubuntu. Проверка из WSL:

```bash
docker version
docker compose version
```

Docker не нужен для быстрого H2-запуска, но нужен для PostgreSQL и полного integration test.

## Clone и checkout

Скопируйте HTTPS clone URL репозитория из Cursor/Origin (**Code → Clone**), затем в WSL:

```bash
cd ~
git clone <HTTPS_CLONE_URL> quiet-clinic
cd quiet-clinic
git fetch origin cursor/fullstack-medical-backend
git checkout -B cursor/fullstack-medical-backend origin/cursor/fullstack-medical-backend
git status
```

`<HTTPS_CLONE_URL>` намеренно не захардкожен: URL этого checkout содержит краткоживущий агентский токен, его нельзя копировать из логов или README.

## Быстрый запуск без Docker

Терминал 1 — backend с in-memory H2:

```bash
cd ~/quiet-clinic
export CLINIC_DEV_KMS_KEY='local-only-demo-key-change-before-sharing'
./mvnw -pl backend/app spring-boot:run -Dspring-boot.run.profiles=local
```

Дождитесь строки `Started MedicalApplication`. Терминал 2 — frontend:

```bash
cd ~/quiet-clinic
npm install
npm run dev -- --host 0.0.0.0 --port 43127
```

Backend: `http://127.0.0.1:8081`  
Frontend: `http://127.0.0.1:43127`  
OpenAPI UI: `http://127.0.0.1:8081/swagger-ui.html`  
Health: `http://127.0.0.1:8081/actuator/health`

Frontend загружает расписание и пациентов из API, создаёт запись и выполняет цепочку приём → заметка → документ → подпись. Если API недоступен, интерфейс явно показывает «Демо-режим» и сохраняет понятный fallback.

Откройте URL из Windows-браузера: WSL 2 проксирует `localhost` автоматически.

## Запуск с PostgreSQL

В WSL, из корня репозитория:

```bash
cd ~/quiet-clinic
docker compose up -d postgres
docker compose ps

umask 077
cat > .env.local <<EOF
DATABASE_URL=jdbc:postgresql://localhost:55432/quiet_clinic
DATABASE_USER=quiet_clinic
DATABASE_PASSWORD=quiet_clinic
CLINIC_DEV_KMS_KEY=$(openssl rand -hex 32)
EOF

set -a
source .env.local
set +a
./mvnw -pl backend/app spring-boot:run
```

В отдельном терминале запустите frontend командами из предыдущего раздела. `.env.local` игнорируется Git. Сохраняйте один и тот же `CLINIC_DEV_KMS_KEY` при работе с существующим volume: смена ключа делает ранее зашифрованные dev-данные нечитаемыми.

Dev credentials `quiet_clinic / quiet_clinic` и demo KMS key допустимы только локально. PostgreSQL опубликован исключительно на `127.0.0.1:55432`; не используйте эти значения в общей или production-среде.

## Demo auth

```bash
curl -H 'X-Demo-Role: REGISTRAR' \
  'http://127.0.0.1:8081/api/v1/patients'

curl -H 'X-Demo-Role: DOCTOR' \
  -H 'X-Actor-Id: 00000000-0000-0000-0000-000000000201' \
  'http://127.0.0.1:8081/api/v1/appointments?date=2026-10-20'
```

Роли: `REGISTRAR`, `DOCTOR`, `NURSE`, `CASHIER`, `MANAGER`. Клинический доступ дополнительно требует активную `care_relationship`.

## Проверки

```bash
./mvnw spotless:apply
./mvnw verify
npm run lint
npm test
npm run build
```

PostgreSQL integration test использует Testcontainers и автоматически пропускается при отсутствии Docker; unit и Modulith boundary tests выполняются всегда.

## Остановка

Остановите backend и frontend через `Ctrl+C` в соответствующих терминалах, затем:

```bash
cd ~/quiet-clinic
docker compose down
```

Удаление dev-БД и всех её данных:

```bash
docker compose down -v
```

## Базовая диагностика

```bash
java -version
./mvnw -version
node --version
npm --version
docker compose ps
docker compose logs --tail=100 postgres
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:8081/v3/api-docs | python3 -m json.tool
curl -I http://127.0.0.1:43127
ss -ltnp | grep -E ':(8081|43127|55432)\b'
```

Если порт 8081 занят:

```bash
PORT=8082 ./mvnw -pl backend/app spring-boot:run -Dspring-boot.run.profiles=local
VITE_API_URL=http://127.0.0.1:8082/api/v1 npm run dev -- --host 0.0.0.0 --port 43127
```

Если frontend показывает «Демо-режим», сначала проверьте health backend, затем CORS/URL через DevTools браузера. При ошибке PostgreSQL смотрите `docker compose logs postgres`; при ошибке Liquibase удаляйте volume только если dev-данные не нужны.
