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

## Быстрый запуск без Docker

```bash
./mvnw -pl backend/app spring-boot:run -Dspring-boot.run.profiles=local
npm install
npm run dev -- --host 0.0.0.0 --port 43127
```

Backend: `http://127.0.0.1:8081`  
Frontend: `http://127.0.0.1:43127`  
OpenAPI UI: `http://127.0.0.1:8081/swagger-ui.html`  
Health: `http://127.0.0.1:8081/actuator/health`

Frontend загружает расписание и пациентов из API, создаёт запись и выполняет цепочку приём → заметка → документ → подпись. Если API недоступен, интерфейс явно показывает «Демо-режим» и сохраняет понятный fallback.

## PostgreSQL

```bash
docker compose up -d postgres
./mvnw -pl backend/app spring-boot:run
```

По умолчанию приложение подключается к `jdbc:postgresql://localhost:55432/quiet_clinic`. Параметры задаются через `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, а dev KMS key — через `CLINIC_DEV_KMS_KEY`.

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
