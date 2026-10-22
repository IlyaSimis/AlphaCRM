# Quiet Clinic — CRM амбулаторной клиники

Внутренняя система клиники: расписание нескольких врачей, карточки пациентов, приём и подписание медицинских документов, аналитика посещаемости и журнал аудита. Frontend на React/MUI, backend на Spring Boot с модульной архитектурой, база PostgreSQL. Все seed-данные синтетические. Проект не заявляет соответствие законодательству или регуляторным требованиям.

## Содержание

- [Быстрый старт](#быстрый-старт)
- [Что запускается](#что-запускается)
- [Управление](#управление)
- [Настройки (.env)](#настройки-env)
- [Перед выходом в сеть](#перед-выходом-в-сеть)
- [Разработка](#разработка)
- [Возможности и API](#возможности-и-api)
- [Архитектура](#архитектура)
- [Проверки](#проверки)
- [Решение проблем](#решение-проблем)

## Быстрый старт

Нужен только [Docker Desktop](https://www.docker.com/products/docker-desktop/). Java, Maven и Node.js для запуска ставить не нужно: всё собирается внутри контейнеров.

Из корня проекта:

```powershell
.\scripts\start.ps1        # Windows PowerShell
```

```bash
./scripts/start.sh         # WSL, Linux, macOS
```

Откройте **http://localhost:8080**. Первая сборка занимает несколько минут, следующие запуски — секунды.

Что делает скрипт:

1. При первом запуске создаёт `.env` со случайным паролем БД и ключом шифрования (`scripts/init-env.sh` или то же внутри `start.ps1`). Существующий `.env` не трогает.
2. Если `.env` новый, а в Docker уже есть база от прошлых запусков, останавливается и объясняет, что делать (иначе пароли не совпадут).
3. Собирает образы и запускает `docker compose up -d --build --wait`: команда завершается, только когда все сервисы прошли healthcheck.
4. При ошибке сам показывает состояние контейнеров и последние строки лога backend.

> **Сохраните копию `.env` в надёжном месте.** В нём ключ шифрования клинических текстов: без него зашифрованные данные в базе прочитать нельзя.

## Что запускается

`compose.yaml` поднимает три сервиса:

| Сервис | Что внутри | Доступ |
|---|---|---|
| `frontend` | nginx без root: статика интерфейса, прокси `/api` → backend, gzip, кэш для файлов сборки, CSP и security headers | `http://127.0.0.1:8080` |
| `backend` | Spring Boot на JRE 21 без root, профиль `prod` | только из nginx, наружу не публикуется |
| `postgres` | PostgreSQL 18, данные в томе `quiet-clinic_quiet-clinic-postgres` | `127.0.0.1:55432` — для IDE и утилит |

Интерфейс и API работают на одном адресе, поэтому CORS не нужен. Все сервисы перезапускаются автоматически (`restart: unless-stopped`) и стартуют по порядку: база → backend → frontend.

Профиль `prod` (`application-prod.yml`):

- graceful shutdown: при остановке текущие запросы дорабатывают до 20 секунд;
- Swagger UI и `/v3/api-docs` выключены, детали health скрыты;
- корректная работа за прокси (`X-Forwarded-*`);
- приложение **не стартует**, если ключ шифрования не задан, короче 32 символов или похож на значение по умолчанию.

## Управление

```bash
docker compose ps                    # состояние и health сервисов
docker compose logs -f backend       # логи backend (frontend, postgres — аналогично)
docker compose up -d --build         # пересобрать и перезапустить после изменений кода
docker compose restart backend       # перезапустить один сервис
docker compose down                  # остановить; данные сохраняются
docker compose down -v               # остановить и УДАЛИТЬ базу со всеми данными
```

В WSL остановка также: `./scripts/stop.sh`.

### Резервная копия базы

Команды работают одинаково в PowerShell и bash. Пользователь и база — из `.env`, по умолчанию `quiet_clinic`.

```bash
# создать копию
docker compose exec postgres pg_dump -U quiet_clinic -Fc -f /tmp/backup.dump quiet_clinic
docker compose cp postgres:/tmp/backup.dump ./backup.dump

# восстановить из копии
docker compose cp ./backup.dump postgres:/tmp/backup.dump
docker compose exec postgres pg_restore -U quiet_clinic -d quiet_clinic --clean --if-exists /tmp/backup.dump
```

Вместе с копией базы храните `.env` с тем же `CLINIC_KMS_KEY`.

## Настройки (.env)

Шаблон — `.env.example`. Файл `.env` в Git и в образы не попадает.

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `POSTGRES_PASSWORD` | случайный | Пароль БД. Применяется только при первом создании базы |
| `CLINIC_KMS_KEY` | случайный | Ключ шифрования клинических текстов, минимум 32 символа. Не меняйте на существующей базе |
| `POSTGRES_DB`, `POSTGRES_USER` | `quiet_clinic` | Имя базы и пользователя |
| `POSTGRES_PORT` | `55432` | Порт базы на localhost |
| `APP_PORT` | `8080` | Порт интерфейса |
| `APP_BIND` | `127.0.0.1` | `127.0.0.1` — доступ только с этого компьютера, `0.0.0.0` — из локальной сети |
| `APP_VERSION` | `local` | Тег собираемых образов |

После изменения `.env` выполните `docker compose up -d`.

## Перед выходом в сеть

Текущая сборка готова к запуску на одном компьютере. Прежде чем открывать её другим людям (`APP_BIND=0.0.0.0` или сервер в интернете):

- **Вход пользователей.** Сейчас роль передаётся демо-заголовком `X-Demo-Role`, и любой, кто откроет страницу, может действовать как администратор. Нужен настоящий IAM (например, OIDC через Keycloak). Backend напоминает об этом предупреждением в логе при каждом старте.
- **HTTPS.** Поставьте перед `frontend` reverse proxy с сертификатом (Caddy, Traefik или nginx).
- **Ключи.** Храните `CLINIC_KMS_KEY` в менеджере секретов; dev-адаптер `KmsPort` замените на KMS-провайдера.
- **Резервные копии** по расписанию (см. [Резервная копия базы](#резервная-копия-базы)).

## Разработка

Для разработки с hot reload нужны **JDK 21 или новее** (проект компилируется под Java 21; JDK 23 из IDEA тоже подойдёт, но для постоянной работы лучше LTS-версия 21 или 25) и **Node.js 22**. Глобальный Maven не нужен: `mvnw` скачает Maven 3.9.11 сам.

### Вариант 1: без базы (H2 в памяти)

Самый быстрый вариант, данные живут до перезапуска backend.

Backend — в IDEA запустите `MedicalApplication` с профилем `local` (Run Configuration → Active profiles: `local`), либо из терминала:

```powershell
.\mvnw.cmd -pl backend/app spring-boot:run "-Dspring-boot.run.profiles=local"
```

```bash
./mvnw -pl backend/app spring-boot:run -Dspring-boot.run.profiles=local
```

Frontend — во втором терминале:

```bash
npm install
npm run dev -- --port 43127
```

| Что | Адрес |
|---|---|
| Интерфейс | http://127.0.0.1:43127 |
| API | http://127.0.0.1:8081/api/v1 |
| Swagger UI | http://127.0.0.1:8081/swagger-ui.html |
| Health | http://127.0.0.1:8081/actuator/health |

Если API недоступен, интерфейс показывает «Демо-режим» и работает на встроенных данных.

### Вариант 2: с PostgreSQL в Docker

Та же база, что и в полном запуске, а backend и frontend — из IDE.

```bash
./scripts/init-env.sh              # один раз, если .env ещё нет
docker compose up -d postgres
```

В Run Configuration для `MedicalApplication` задайте переменные окружения (значения — из `.env`):

```
DATABASE_PASSWORD=<POSTGRES_PASSWORD из .env>
CLINIC_DEV_KMS_KEY=<CLINIC_KMS_KEY из .env>
```

Или из bash:

```bash
set -a; source .env; set +a
export DATABASE_PASSWORD="$POSTGRES_PASSWORD" CLINIC_DEV_KMS_KEY="$CLINIC_KMS_KEY"
./mvnw -pl backend/app spring-boot:run
```

Frontend запускается так же, как в варианте 1. Не запускайте одновременно контейнер `backend` и backend из IDE на одной базе с разными ключами шифрования.

## Возможности и API

Роли в интерфейсе: «Врач», «Администратор», «Менеджер», «Кассир». Запросы уходят с `X-Actor-Id` выбранного демо-сотрудника, поэтому в журнале аудита видно, кто выполнил действие. Для роли «Врач» рядом с переключателем роли выбирается врач — Александр Петров (`…0201`, по умолчанию) или Ольга Лебедева (`…0202`); его id уходит в `X-Actor-Id`.

| Сотрудник | Роль API | X-Actor-Id |
|---|---|---|
| Александр Петров / Ольга Лебедева | `DOCTOR` | `00000000-0000-0000-0000-000000000201` / `…0202` |
| Елена Смирнова | `ADMINISTRATOR` | `00000000-0000-0000-0000-000000000901` |
| Игорь Кузнецов | `MANAGER` | `00000000-0000-0000-0000-000000000902` |
| Наталья Орлова | `CASHIER` | `00000000-0000-0000-0000-000000000903` |

Время во всех запросах — UTC (`2026-10-20T09:00:00Z`); дни недели в рабочих часах — ISO (понедельник = 1).

### Расписание (`scheduling`)

| Маршрут | Кто | Что делает |
|---|---|---|
| `GET /api/v1/practitioners` | все роли | врачи с рабочими часами |
| `GET /api/v1/services` | все роли | справочник услуг: название, длительность, цена в копейках, валюта |
| `GET /api/v1/appointments?date=` | все роли | записи за день; фильтры `practitionerId`, `patientId`, `status` |
| `GET /api/v1/appointments?from=&to=` | все роли | записи за период (до трёх лет): история пациента (`patientId`), завершённые приёмы для кассы (`status=COMPLETED`) |
| `POST /api/v1/appointments` | `ADMINISTRATOR`, `DOCTOR` | новая запись; `serviceName` из справочника, на запись сохраняется снимок цены (`amountMinor`, `currency`) |
| `POST /api/v1/appointments/{id}/reschedule` | `ADMINISTRATOR` | перенос `{startsAt, endsAt, practitionerId}` из `BOOKED`/`CONFIRMED`, статус не меняется, в outbox ничего не уходит |
| `POST /api/v1/appointments/{id}/status` | `ADMINISTRATOR`, `DOCTOR` | смена статуса |
| `POST /api/v1/appointments/{id}/cancel` | `ADMINISTRATOR` | отмена активной записи |

Правила записи и переноса: интервал целиком внутри смены врача в этот день недели (иначе `409 OUTSIDE_WORKING_HOURS`, форма показывает ту же ошибку до отправки), без пересечений с другими записями врача (`409 SLOT_ALREADY_BOOKED`, дополнительно PostgreSQL exclusion constraint), конец позже начала (`409 INVALID_INTERVAL`), услуга из справочника (`409 UNKNOWN_SERVICE`).

Статусы: администратор — `BOOKED → CONFIRMED`, `BOOKED/CONFIRMED → ARRIVED | NO_SHOW`; врач, только свои записи — `ARRIVED → IN_PROGRESS`, `ARRIVED/IN_PROGRESS → COMPLETED`. Недопустимый переход — `409 INVALID_STATUS_TRANSITION`. Неявка освобождает слот, завершённый приём — нет. При переходе в `COMPLETED` модуль оплаты создаёт один счёт `PENDING` на снимок цены записи.

При создании записи (и при переносе к другому врачу) расписание вызывает `CareRelationshipService.ensureCareRelationship(practitionerId, patientId)`: если активной связи с лечением нет, создаётся новая на год.

### Пациенты (`patients`)

| Маршрут | Кто | Что делает |
|---|---|---|
| `GET /api/v1/patients[?query=]` | все роли | поиск по ФИО |
| `POST /api/v1/patients` | `ADMINISTRATOR` | регистрация с первым согласием |
| `GET /api/v1/patients/{id}` | все роли | карточка |
| `PUT /api/v1/patients/{id}` | `ADMINISTRATOR` | ФИО, дата рождения, телефон; в аудит — имена изменённых полей без значений |
| `POST /api/v1/patients/{id}/consents` | `ADMINISTRATOR` | новая действующая версия согласия `{version, basis}`; прежние не удаляются |
| `POST /api/v1/patients/{id}/consents/{consentId}/revoke` | `ADMINISTRATOR` | отзыв: ставит `revokedAt`, повторный отзыв — `409 ALREADY_REVOKED` |

Согласие «действует», если есть хотя бы одно без `revokedAt`. В аудит — факт и id согласия, без текста основания.

### Клинический контур (`clinical`)

Доступ к тексту — только врачу с активной `care_relationship`; администратору, менеджеру и кассиру клинический текст недоступен.

| Маршрут | Что делает |
|---|---|
| `POST /api/v1/clinical/encounters` | начать приём по записи `{appointmentId, patientId, practitionerId}`. Запись должна существовать, пациент и врач — совпадать с ней, врач — быть текущим `X-Actor-Id`, статус — `ARRIVED` или `IN_PROGRESS`. `201` — новый приём, `200` — уже начатый по этой записи |
| `GET /api/v1/clinical/patients/{patientId}/encounters` | история приёмов: `appointmentId`, `startedAt`, текст заметки и список версий документа (`id`, `state`, `templateVersion`, `signedAt`, `amendsDocumentId`) — без snapshot и текста документа |
| `PUT /api/v1/clinical/encounters/{id}/note` | черновик заметки: жалобы, психический статус, диагноз, рекомендации |
| `POST /api/v1/clinical/encounters/{id}/documents` | черновик документа `{templateVersion}` из текущей заметки (`409 NOTE_REQUIRED`, если заметки нет) |
| `POST /api/v1/clinical/documents/{id}/sign` | подпись: черновик приводится к текущей заметке, считается хеш, версия блокируется (`SIGNED_LOCKED`) |
| `POST /api/v1/clinical/documents/{id}/amendments` | дополнение к подписанному документу — новый черновик со ссылкой на исходный; исходник остаётся заблокированным |

Клинический модуль не зависит от расписания: данные записи он получает через свой порт `VisitAppointments`, который реализует `scheduling`.

### Касса (`billing`)

| Маршрут | Кто | Что делает |
|---|---|---|
| `GET /api/v1/billing/charges` | `CASHIER`, `ADMINISTRATOR` | счета с оплатой: `appointmentId`, сумма, статус, `paymentStatus` |
| `POST /api/v1/billing/charges` | `CASHIER`, `ADMINISTRATOR` | счёт по приёму, если его ещё нет (`409 CHARGE_ALREADY_EXISTS`) |
| `POST /api/v1/billing/charges/{id}/payments` | `CASHIER` | оплата `{idempotencyKey}`; повтор с тем же ключом не списывает второй раз |

Один счёт на приём — уникальный индекс по `appointment_id`. Экран «Касса» (кассир и администратор): завершённые приёмы за 90 дней — пациент, врач, услуга, сумма, статус счёта; без диагнозов и текста приёма. Администратор выставляет недостающий счёт, кассир принимает оплату. На экран аналитики деньги не выводятся.

### Аналитика и аудит

- **Аналитика**: `GET /api/v1/analytics/appointments?from=&to=[&practitionerId=]` для `MANAGER`/`ADMINISTRATOR` — агрегаты «дата × врач × статус» без персональных данных.
- **Журнал аудита**: `GET /api/v1/audit[?result=DENIED&objectType=PATIENT]` для `MANAGER`/`ADMINISTRATOR`. Колонка «Основание» — короткий машинный код без клинического текста, телефонов, ФИО и значений полей; интерфейс переводит его на русский (`search` → «из поиска», `fullName,phone` → «изменены: ФИО, телефон», `BOOKED->CONFIRMED` → «Ожидает → Подтверждён», перенос → «20.10.2026 09:00 → 22.10.2026 10:30»). Коды, которые повторяют действие, показываются как «—», неизвестные — как есть.

### Demo auth

Роль и автор действия передаются заголовками. Через Docker API доступен на `http://localhost:8080/api/v1`, при разработке — на `http://127.0.0.1:8081/api/v1`.

```bash
curl -H 'X-Demo-Role: ADMINISTRATOR' 'http://localhost:8080/api/v1/patients'

curl -H 'X-Demo-Role: DOCTOR' \
  -H 'X-Actor-Id: 00000000-0000-0000-0000-000000000201' \
  'http://localhost:8080/api/v1/appointments?date=2026-10-20'
```

Роли: `ADMINISTRATOR`, `DOCTOR`, `NURSE`, `CASHIER`, `MANAGER`.

## Архитектура

- Maven reactor с backend-приложением `backend/app`: Spring Boot 4.1.1, Spring Modulith 2.1.1, JPA, Liquibase, PostgreSQL 18;
- модули: patients, scheduling (включая аналитику), clinical/documents, billing, communications/outbox, audit и platform; границы проверяет `ModularityTest`;
- запрет пересекающихся записей врача: проверка в приложении плюс PostgreSQL exclusion constraint;
- клинические тексты и snapshot документа шифруются AES-256-GCM через заменяемый `KmsPort`; dev-адаптер берёт ключ из `CLINIC_DEV_KMS_KEY`;
- подписанный документ (`SIGNED_LOCKED`) защищён доменным инвариантом и PostgreSQL trigger; исправление — новый связанный документ;
- платежи и уведомления — порты с безопасными mock-адаптерами;
- аудит фиксирует чтения и изменения без клинического текста.

### Структура кода

Код разложен по разделам; тест лежит рядом с тем, что он проверяет.

**Frontend** (`src/`): тест — в той же папке, с тем же именем и суффиксом `.test`.

```
src/
  app/          App.tsx (каркас и навигация), Sidebar, Topbar, theme.tsx,
                useClinicData.ts (данные и действия: сервер или демо-режим), demoData.ts
  api/          client.ts (адрес, заголовки, выбранный врач, ошибки), schedule.ts, patients.ts,
                clinical.ts, billing.ts, analytics.ts, audit.ts, types.ts
  features/
    schedule/   Schedule.tsx, BookingDialog.tsx, RescheduleDialog.tsx, booking.ts,
                statusActions.ts, workingHours.ts
    patients/   PatientsList.tsx, PatientCard.tsx, PatientDialog.tsx, ConsentDialog.tsx,
                history.ts, usePatientHistory.ts, useClinicalHistory.ts, search.ts
    visit/      Visit.tsx, SignDialog.tsx, useVisit.ts, visit.ts
    cashier/    Cashier.tsx, cashier.ts
    analytics/  Analytics.tsx, stats.ts
    audit/      AuditLog.tsx, auditLabels.ts
  shared/       dates.ts, roles.ts, storage.ts, text.ts, ui.tsx (Metric, Info, FormSection)
  test/         setup.ts (общая подготовка), helpers.tsx (renderApp, chooseRole, goOffline)
```

Два вида тестов: `*.test.ts` — функции без интерфейса (`stats.test.ts`, `statusActions.test.ts`, `dates.test.ts`), `<раздел>.test.tsx` — сценарии раздела через весь интерфейс (`schedule.test.tsx`, `patients.test.tsx`).

**Backend** (`backend/app/src/main/java/ru/quietclinic/<модуль>/`): один тип — один файл. В каждом модуле:

- сущности и репозитории (`Appointment`, `AppointmentRepository`);
- запросы и ответы API (`CreateAppointmentRequest`, `AppointmentView`);
- правила и логика — в сервисах и доменных классах (`AppointmentService`, `AppointmentStatusPolicy`, `Patient.update`);
- контроллер — только HTTP, без логики (`SchedulingController`).

Тесты — по стандарту Maven в `src/test/java`, в том же пакете, класс `FooTest` проверяет класс `Foo`. В IntelliJ IDEA переход между классом и его тестом — `Ctrl+Shift+T`.

| Тест | Что проверяет | Spring нужен? |
|---|---|---|
| `AppointmentStatusPolicyTest`, `WorkingHoursPolicyTest`, `RescheduleReasonTest`, `AppointmentStatsServiceTest`, `PatientTest`, `MedicalDocumentTest`, `NoteContentTest`, `ProductionReadinessCheckTest` | правила и расчёты | нет, миллисекунды |
| `SchedulingControllerTest`, `ServiceCatalogControllerTest`, `AnalyticsControllerTest`, `PatientsControllerTest`, `ClinicalControllerTest`, `BillingControllerTest`, `AuditControllerTest` | HTTP-сценарии с правами ролей на H2 | да |
| `CareRelationshipServiceTest`, `ChargeServiceTest` | публичные методы модулей, которые вызывает расписание | да |
| `PostgresAppointmentIntegrationTest` | ограничение на пересечение записей в настоящем PostgreSQL | Docker |
| `ModularityTest` | границы между модулями | нет |

Файлы запуска:

```
compose.yaml                  # полный стек
.env.example                  # шаблон настроек
deploy/backend.Dockerfile     # Maven → слоёный образ на JRE 21
deploy/frontend.Dockerfile    # Vite build → nginx-unprivileged
deploy/nginx.conf             # прокси /api, SPA, кэш, healthz
deploy/security-headers.conf  # CSP и другие заголовки
scripts/start.ps1, start.sh   # запуск одной командой
scripts/init-env.sh           # генерация .env
scripts/stop.sh               # остановка
```

## Проверки

```bash
./mvnw spotless:apply   # форматирование Java
./mvnw verify           # тесты backend + проверка форматирования
npm run lint
npm test                # все тесты frontend
npx vitest src/features/schedule   # тесты одного раздела
npm run build
```

Один класс backend: `./mvnw -pl backend/app test -Dtest=AppointmentStatusPolicyTest` или зелёная стрелка рядом с тестом в IDEA. PostgreSQL integration test использует Testcontainers и пропускается, если Docker не запущен.

## Решение проблем

**`password authentication failed for user "quiet_clinic"` в логе backend.** В Docker осталась база от прошлых запусков с другим паролем: пароль из `.env` применяется только при создании базы. Если данные не нужны — `docker compose down -v` и снова `start`. Если нужны — впишите в `.env` прежние `POSTGRES_PASSWORD` и `CLINIC_KMS_KEY`.

**Backend не стартует с сообщением про `CLINIC_DEV_KMS_KEY`.** В `.env` нет `CLINIC_KMS_KEY` или он короче 32 символов. Удалите `.env` (только если базы ещё нет) и запустите скрипт заново, либо впишите ключ вручную.

**`Расшифрование не выполнено` при открытии приёма.** Ключ в `.env` не совпадает с тем, которым шифровались данные. Верните прежний `CLINIC_KMS_KEY`.

**Порт 8080 или 55432 занят.** Поменяйте `APP_PORT` или `POSTGRES_PORT` в `.env` и выполните `docker compose up -d`.

**`start.sh` падает с `$'\r': command not found`.** Скрипт получил Windows-переводы строк. В репозитории это исправляет `.gitattributes`; для текущей копии: `sed -i 's/\r$//' scripts/*.sh`.

**Интерфейс показывает «Демо-режим».** Backend недоступен: проверьте `docker compose ps` и `docker compose logs backend`. При разработке — `http://127.0.0.1:8081/actuator/health`.

**Смотреть логи и состояние:**

```bash
docker compose ps -a
docker compose logs --tail 100 backend
docker compose logs --tail 100 postgres
```
