# AlphaCRM — CRM амбулаторной клиники

Внутренняя система клиники: вход сотрудников по логину и паролю, расписание нескольких врачей по дням и неделям, регистратура (задачи и лист ожидания), карточки пациентов, приём и подписание медицинских документов, касса, аналитика посещаемости и журнал аудита. Frontend на React/MUI, backend на Spring Boot с модульной архитектурой, база PostgreSQL. Все seed-данные синтетические. Проект не заявляет соответствие законодательству или регуляторным требованиям.

Сотрудник видит продукт как **AlphaCRM**. Внутренние имена прежние — пакет `ru.quietclinic`, Maven-координаты, имя Compose-проекта, база и пользователь `quiet_clinic`: от них зависит уже созданная база и её том, поэтому они не переименовываются.

Подробная документация: [архитектура проекта](docs/architecture.md) и [хранение данных — схема базы](docs/data-model.md).

## Содержание

- [Быстрый старт](#быстрый-старт)
- [Что запускается](#что-запускается)
- [Управление](#управление)
- [Настройки (.env)](#настройки-env)
- [Вход сотрудников](#вход-сотрудников)
- [Перед выходом в сеть](#перед-выходом-в-сеть)
- [Разработка](#разработка)
- [Возможности и API](#возможности-и-api)
- [Внешние выводы: сообщения пациентам и чеки](#внешние-выводы-сообщения-пациентам-и-чеки)
- [Архитектура](#архитектура) · [docs/architecture.md](docs/architecture.md) · [docs/data-model.md](docs/data-model.md)
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

Откройте **http://localhost:8080** и войдите (см. [Вход сотрудников](#вход-сотрудников)). Первая сборка занимает несколько минут, следующие запуски — секунды.

Что делает скрипт:

1. При первом запуске создаёт `.env` со случайным паролем БД, ключом шифрования и паролем начального администратора и печатает этот пароль (`scripts/init-env.sh` или то же внутри `start.ps1`). В существующем `.env` ничего не меняет, только дописывает `CLINIC_ADMIN_PASSWORD`, если его там ещё нет (`.env` от прошлой версии; прежний `CLINIC_BOOTSTRAP_PASSWORD` удаляется).
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
- приложение **не стартует**, если ключ шифрования не задан, короче 32 символов или похож на значение по умолчанию;
- приложение **не стартует** без `CLINIC_ADMIN_PASSWORD`, если он короче 12 символов или равен `admin`;
- начальный администратор обязан сменить пароль при первом входе, до остальных страниц.

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
| `CLINIC_NAME` | `AlphaCRM` | Название клиники, которую приложение создаёт на пустой базе |
| `CLINIC_ADMIN_NAME` | `Администратор` | ФИО начального администратора на пустой базе |
| `CLINIC_ADMIN_PASSWORD` | случайный | Пароль начального администратора (логин `admin`) для пустой базы. Обязателен, минимум 12 символов, не `admin`. Хеш bcrypt считается при старте; в базе только хеш |
| `SESSION_COOKIE_SECURE` | `false` | `true` — cookie сессии только по HTTPS. Включите за reverse proxy с сертификатом |
| `CLINIC_OUTBOUND_WEBHOOK_URL` | пусто | Вебхук клиники для сообщений пациентам (свой бот, SMS-шлюз). Пусто — канал выключен |
| `CLINIC_OUTBOUND_WEBHOOK_SECRET` | пусто | Секрет подписи HMAC-SHA256 запросов вебхука. Обязателен при заданном URL, минимум 32 символа |
| `TELEGRAM_BOT_TOKEN` | пусто | Токен бота Telegram. Используется, только если вебхук не задан |
| `CLINIC_CASHBOX_URL`, `CLINIC_CASHBOX_SECRET` | пусто | Онлайн-касса для чеков и секрет подписи (минимум 32 символа). Пусто — чек остаётся `QUEUED` |
| `CLINIC_TIME_ZONE` | `UTC` | Часовой пояс клиники (IANA, например `Europe/Moscow`). В нём расписание, смены врачей, «сегодня», журнал и сообщения пациентам; в базе — моменты UTC. Записи, сделанные при другом поясе, после смены сдвинутся на экране на разницу поясов |
| `CLINIC_OUTBOX_ENABLED`, `CLINIC_OUTBOX_POLL_INTERVAL` | `true`, `PT30S` | Доставщик outbox и пауза между проходами |

После изменения `.env` выполните `docker compose up -d`.

## Вход сотрудников

Учётные записи хранятся в базе (`staff_user`): клиника, ФИО, логин, bcrypt-хеш пароля, роль, карточка врача (только у врача), признак «активен» и «сменить пароль при входе». Внешний OIDC/Keycloak не используется. Зашитых сотрудников нет: ни паролей, ни хешей в коде и миграциях.

**Первый запуск.** Если в базе нет ни одного сотрудника, приложение создаёт клинику (название из `CLINIC_NAME`) и одного администратора с логином **`admin`** и паролем из `CLINIC_ADMIN_PASSWORD`; хеш считается при старте bcrypt со своей солью.

| Профиль | Вход | Смена пароля |
|---|---|---|
| `local` (H2, разработка) | **`admin` / `admin`** (`application-local.yml`) | не требуется |
| `prod` (Docker) | `admin` / пароль из `CLINIC_ADMIN_PASSWORD` в `.env` | обязательна при первом входе |

Дальше людей заводит администратор в разделе «Сотрудники»: имя, логин, роль, временный пароль, который он вводит сам. Пароль показывается один раз, в базе лежит только хеш. При первом входе сотрудник обязан задать свой пароль — до этого API отвечает `403 PASSWORD_CHANGE_REQUIRED` на всё, кроме `/auth/me`, `/auth/password`, `/auth/logout`. Врач связывается с карточкой врача своей клиники.

Демо-врачи (Петров, Лебедева) и демо-пациенты остаются в расписании локальной базы, но учётных записей у них нет: войти под ними нельзя, пока администратор сам не заведёт сотрудника и не свяжет его с карточкой врача. Учётные записи прежних демо-логинов (`smirnova`, `petrov` и др.) удаляет миграция `quietclinic:016`. Старый changeset `quietclinic:013` не переписывается (Liquibase сверяет контрольные суммы), поэтому прежний хеш остаётся в его тексте, но в базе после 016 его нет.

**Несколько клиник.** У `staff_user`, `practitioner` и `patient` есть `clinic_id`, сессия знает клинику сотрудника, и пациенты, врачи, записи, сотрудники, задачи, счета, журнал и архив документов ограничены ею. Вторая клиника добавляется строкой в `clinic` и своим администратором в `staff_user` (логин уникален во всей установке) — без правки кода. Общего списка сотрудников на все клиники и экрана выбора клиники нет.

Как устроено:

- `POST /api/v1/auth/login {username, password}` открывает сессию: cookie `JSESSIONID` с `HttpOnly` и `SameSite=Lax`, срок 8 часов. Неверный логин и неверный пароль дают одинаковый `401 INVALID_CREDENTIALS`; отключённый сотрудник войти не может; пароль в лог не пишется. После 5 неудач подряд на один логин с одного адреса (или 20 с адреса вообще) вход с этого адреса закрыт на 15 минут — `429 TOO_MANY_LOGIN_ATTEMPTS`. Счётчики в памяти backend; настройки `clinic.login.*`.
- `GET /api/v1/auth/me` — текущий сотрудник (без хеша), `POST /api/v1/auth/logout` — выход, `POST /api/v1/auth/password {currentPassword, newPassword}` — смена своего пароля (нужен текущий, новый — от 8 символов).
- Роль и автор действия сервер берёт из сессии. Для врача автор (`actorId`) — id его карточки врача, поэтому записи журнала аудита и связь с лечением остались прежними. Заголовки `X-Demo-Role` / `X-Actor-Id` больше ничего не решают.
- CSRF включён: сервер кладёт токен в cookie `XSRF-TOKEN`, интерфейс отправляет его заголовком `X-XSRF-TOKEN` на изменяющих запросах. Все запросы идут с `credentials: 'include'`. Интерфейс и API работают на одном origin (nginx в Docker, прокси Vite при разработке); CORS для локальной разработки разрешает cookie только с явных адресов из `CLINIC_CORS_ORIGINS`, не `*`.
- Без входа видна только страница входа; `401` от API возвращает на неё. Если сервер недоступен, на странице входа можно открыть **песочницу** с одной из ролей («Песочница: Врач» и т. п.): без людей и логинов, помечена в шапке, работает на встроенных данных в памяти вкладки и ничего не отправляет на сервер.
- Раздел «Сотрудники» (только администратор своей клиники): создать сотрудника с временным паролем, отключить или включить, задать новый временный пароль. Себя отключить нельзя; врачу нужна существующая карточка врача этой клиники, остальным ролям — никакой; роль после создания не меняется; хеши паролей наружу не отдаются.

Вход из командной строки:

```bash
# токен CSRF и cookie сессии сохраняются в cookies.txt
curl -c cookies.txt -b cookies.txt -s http://localhost:8080/api/v1/auth/csrf
TOKEN=$(grep XSRF-TOKEN cookies.txt | awk '{print $7}')
curl -c cookies.txt -b cookies.txt -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<CLINIC_ADMIN_PASSWORD>"}' http://localhost:8080/api/v1/auth/login
curl -b cookies.txt 'http://localhost:8080/api/v1/patients'
```

При разработке (`local`) — `admin` / `admin` и адрес `http://127.0.0.1:8081`.

## Перед выходом в сеть

Текущая сборка готова к запуску на одном компьютере. Прежде чем открывать её другим людям (`APP_BIND=0.0.0.0` или сервер в интернете):

- **Пароли.** Сразу после первого входа задайте каждому сотруднику личный пароль в разделе «Сотрудники» (или попросите сменить свой через «Сменить пароль»): начальный пароль из `.env` общий для всех.
- **HTTPS.** Поставьте перед `frontend` reverse proxy с сертификатом (Caddy, Traefik или nginx) и включите `SESSION_COOKIE_SECURE=true`. Для Caddy (`/etc/caddy/Caddyfile`), со скрытием заголовка `Server`:

  ```
  crm.example.ru {
      header -Server
      reverse_proxy 127.0.0.1:8080 {
          header_down -Server
      }
  }
  ```
- **Часовой пояс.** Задайте `CLINIC_TIME_ZONE` (например `Europe/Moscow`) до того, как вносить настоящие записи.
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

`npm run dev` проксирует `/api` на `http://127.0.0.1:8081` (адрес можно сменить переменной `VITE_API_PROXY`), поэтому cookie сессии и CSRF работают так же, как за nginx. Вход — `admin` / `admin`; остальных сотрудников заведите в разделе «Сотрудники». Если API недоступен, на странице входа доступна песочница по ролям.

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

Роли: «Врач», «Администратор», «Менеджер», «Кассир» (в API — `DOCTOR`, `ADMINISTRATOR`, `MANAGER`, `CASHIER`). Роль и врач определяются учётной записью, под которой выполнен вход, — переключателей в шапке нет. Все маршруты, кроме входа, требуют сессии (`401 UNAUTHORIZED` без неё).

**Ошибки.** Ответ с ошибкой — `{timestamp, status, code, message[, fields]}`. `code` — машинный код на английском (`SLOT_ALREADY_BOOKED`, `SERVICE_INACTIVE`, `NO_CARE_RELATIONSHIP`…), `message` — английское пояснение для разработчика. Русскую фразу выбирает интерфейс по коду (`src/api/errorMessages.ts`); если кода там ещё нет, показывается `message` сервера, но не сам код. Неверный логин или пароль — `401 INVALID_CREDENTIALS`, одинаково для обоих случаев.

| Маршрут | Кто | Что делает |
|---|---|---|
| `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `POST /api/v1/auth/password`, `GET /api/v1/auth/csrf` | все | вход, выход, текущий сотрудник, смена своего пароля, выдача CSRF-cookie |
| `GET /api/v1/staff` | `ADMINISTRATOR` | сотрудники без хешей паролей |
| `POST /api/v1/staff` | `ADMINISTRATOR` | `{fullName, username, password, role, practitionerId}`: `409 USERNAME_TAKEN`, `PRACTITIONER_REQUIRED`, `PRACTITIONER_TAKEN`, `PRACTITIONER_NOT_ALLOWED`, `404` для неизвестного врача |
| `POST /api/v1/staff/{id}/deactivate`, `…/activate` | `ADMINISTRATOR` | отключить (себя — `409 CANNOT_DEACTIVATE_SELF`) или включить; сессии отключённого перестают действовать |
| `POST /api/v1/staff/{id}/password` | `ADMINISTRATOR` | новый пароль `{newPassword}`, от 8 символов |

Время во всех запросах — UTC (`2026-10-20T09:00:00Z`); дни недели в рабочих часах — ISO (понедельник = 1).

### Расписание (`scheduling`)

| Маршрут | Кто | Что делает |
|---|---|---|
| `GET /api/v1/practitioners` | все роли | врачи с рабочими часами |
| `GET /api/v1/services[?all=true]` | все роли | справочник услуг своей клиники: название, длительность, цена в копейках, валюта, `active`. Выключенные — только администратору с `all=true` |
| `GET /api/v1/appointments?date=` | все роли | записи за день; фильтры `practitionerId`, `patientId`, `status` |
| `GET /api/v1/appointments?from=&to=` | все роли | записи за период (до трёх лет): история пациента (`patientId`), завершённые приёмы для кассы (`status=COMPLETED`) |
| `POST /api/v1/appointments` | `ADMINISTRATOR`, `DOCTOR` | новая запись; `serviceName` — включённая услуга своей клиники (`409 UNKNOWN_SERVICE`, выключенная — `409 SERVICE_INACTIVE`), на запись сохраняется снимок названия и цены (`serviceName`, `amountMinor`, `currency`) |
| `POST /api/v1/appointments/{id}/reschedule` | `ADMINISTRATOR` | перенос `{startsAt, endsAt, practitionerId}` из `BOOKED`/`CONFIRMED`, статус не меняется, в outbox ничего не уходит |
| `POST /api/v1/appointments/{id}/status` | `ADMINISTRATOR`, `DOCTOR` | смена статуса |
| `POST /api/v1/appointments/{id}/cancel` | `ADMINISTRATOR` | отмена активной записи |
| `PUT /api/v1/practitioners/{id}/working-hours` | `ADMINISTRATOR` | недельный график целиком `{shifts: [{dayOfWeek, startsAt, endsAt}]}`; дня нет в списке — выходной. `409 INVALID_SHIFT` при повторе дня или конце не позже начала |
| `POST /api/v1/services` | `ADMINISTRATOR` | новая услуга `{name, durationMinutes, priceMinor, currency: "RUB"}`: название до 255 символов, длительность 1–720 минут, цена в копейках ≥ 0. Сразу доступна в записи. Повтор названия в клинике — `409 SERVICE_NAME_TAKEN` |
| `PUT /api/v1/services/{id}/price` | `ADMINISTRATOR` | новая цена `{priceMinor}` в копейках. Действует для новых записей; `amountMinor` уже созданных записей не меняется |
| `POST /api/v1/services/{id}/deactivate`, `…/activate` | `ADMINISTRATOR` | выключить или снова включить услугу. Выключенная не предлагается в новой записи и в листе ожидания, но не удаляется: старые записи хранят свой снимок |

Расписание открывается на сегодняшнем дне (или на дне, выбранном в прошлый раз) и показывается по дням или по неделе (с понедельника): в неделе те же записи и те же действия, что в дне, неделя загружается одним запросом `?from=&to=`. День без смены врача помечен «Выходной», запись на него недоступна. График, цены и справочник услуг ведёт администратор на странице «График и цены»: добавить услугу, сменить цену, выключить или включить. Форма записи берёт список услуг только с сервера; две начальные услуги («Первичный приём», «Повторный приём») — данные миграции, а не массив в коде. Демо-песочница без сервера показывает те же две услуги из `demoServices`.

Правила записи и переноса: интервал целиком внутри смены врача в этот день недели (иначе `409 OUTSIDE_WORKING_HOURS`, форма показывает ту же ошибку до отправки), без пересечений с другими записями врача (`409 SLOT_ALREADY_BOOKED`, дополнительно PostgreSQL exclusion constraint), конец позже начала (`409 INVALID_INTERVAL`), не в прошлом (`409 APPOINTMENT_IN_PAST`, форма тоже не пропускает прошедшее время), услуга из справочника (`409 UNKNOWN_SERVICE`). Смены и день записи — в поясе клиники `CLINIC_TIME_ZONE`.

Статусы: администратор — `BOOKED → CONFIRMED`, `BOOKED/CONFIRMED → ARRIVED | NO_SHOW`; врач, только свои записи — `BOOKED/CONFIRMED/ARRIVED → IN_PROGRESS`, `ARRIVED/IN_PROGRESS → COMPLETED`. Недопустимый переход — `409 INVALID_STATUS_TRANSITION`. Неявка освобождает слот, завершённый приём — нет. При переходе в `COMPLETED` модуль оплаты создаёт один счёт `PENDING` на снимок цены записи.

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

| `PUT /api/v1/patients/{id}/registry` | `ADMINISTRATOR` | административные поля `{referralSource, registryNote, telegramChatId, removeTelegram}`: chat id Telegram пациента (только цифры; пусто — не менять, `removeTelegram: true` — отключить; в ответе — только `telegramConnected`, сам chat id сервер не отдаёт никому); откуда пришёл (`RECOMMENDATION`, `WEBSITE`, `INSURANCE`, `DOCTOR_REFERRAL`, `OTHER`, иначе `409 INVALID_REFERRAL_SOURCE`) и заметка регистратуры до 1000 символов |

Согласие «действует», если есть хотя бы одно без `revokedAt`. В аудит — факт и id согласия, без текста основания.

Карточка отдаёт `phoneSharedWith` — другие карточки с тем же телефоном (сравниваются цифры, `8…` = `+7…`); интерфейс показывает предупреждение о возможном дубле. Заметка регистратуры в клинический контур не попадает, врачу в интерфейсе не показывается; в аудит пишется только факт изменения и имена полей (`referralSource,registryNote`), без текста.

### Регистратура (`registry`)

Внутренние задачи сотрудникам и лист ожидания. Это не отправка SMS: пациенту ничего не уходит, outbox не используется.

| Маршрут | Кто | Что делает |
|---|---|---|
| `GET /api/v1/registry/tasks?dueTo=` | `ADMINISTRATOR`, `MANAGER`, `CASHIER` | открытые задачи клиники со сроком до даты включительно — список «на сегодня», с пациентом и без |
| `GET /api/v1/registry/tasks?patientId=` | те же | все задачи пациента — в карточке; задачи без пациента сюда не попадают |
| `POST /api/v1/registry/tasks` | `ADMINISTRATOR` | `{title, dueDate, assigneeId, patientId?}`: `title` — свободный текст до 200 символов, исполнитель — активный сотрудник своей клиники (`409 INVALID_ASSIGNEE`), пациент необязателен (`404 PATIENT_NOT_FOUND` для чужого или неизвестного) |
| `POST /api/v1/registry/tasks/{id}/done`, `…/reopen` | исполнитель или `ADMINISTRATOR` | статус `OPEN` ↔ `DONE` |
| `GET /api/v1/registry/waitlist[?practitionerId=&date=]` | `ADMINISTRATOR`, `MANAGER` | активный лист ожидания; с фильтром — кто подходит под свободный слот |
| `POST /api/v1/registry/waitlist` | `ADMINISTRATOR` | `{patientId, practitionerId, serviceId, wishFrom, wishTo}` |
| `POST /api/v1/registry/waitlist/{id}/book` | `ADMINISTRATOR` | закрыть ожидание ссылкой `{appointmentId}` на запись, уже созданную обычным `POST /appointments` |
| `POST /api/v1/registry/waitlist/{id}/cancel` | `ADMINISTRATOR` | убрать из листа |

Задача — текст администратора, а не выбор из списка. Задачи, созданные до этого, сохранились: прежние виды стали названием («Перезвонить», «Обновить согласие»). В аудит пишется только `with-patient` или `without-patient`, без текста задачи.

Когда запись отменили или отметили неявку, у слота в расписании появляется кнопка «Ждут: N» с подходящими ожидающими (тот же врач, день внутри желаемого периода). «Записать» создаёт обычную запись — с проверкой смены и пересечений — и закрывает ожидание.

### Клинический контур (`clinical`)

Доступ к тексту — только врачу с активной `care_relationship`; администратору, менеджеру и кассиру клинический текст недоступен.

| Маршрут | Что делает |
|---|---|
| `POST /api/v1/clinical/encounters` | начать приём по записи `{appointmentId, patientId, practitionerId}`. Запись должна существовать, пациент и врач — совпадать с ней, врач — тем, кто вошёл. Начать можно по любой своей записи, кроме отменённой, неявки и завершённой (`409 APPOINTMENT_NOT_STARTABLE`); из «Ожидает», «Подтверждён» или «Прибыл» запись переходит в `IN_PROGRESS`. Несколько записей врача могут быть в `IN_PROGRESS` одновременно. `201` — новый приём, `200` — уже начатый по этой записи (тот же encounter) |
| `GET /api/v1/clinical/patients/{patientId}/encounters` | история приёмов: `appointmentId`, `startedAt`, текст заметки и список версий документа (`id`, `state`, `templateVersion`, `signedAt`, `amendsDocumentId`) — без snapshot и текста документа |
| `PUT /api/v1/clinical/encounters/{id}/note` | черновик заметки: жалобы, психический статус, диагноз, рекомендации |
| `POST /api/v1/clinical/encounters/{id}/documents` | черновик документа `{templateVersion}` из текущей заметки (`409 NOTE_REQUIRED`, если заметки нет) |
| `POST /api/v1/clinical/documents/{id}/sign` | подпись: черновик приводится к текущей заметке, считается хеш, версия блокируется (`SIGNED_LOCKED`) |
| `POST /api/v1/clinical/documents/{id}/amendments` | дополнение к подписанному документу — новый черновик со ссылкой на исходный; исходник остаётся заблокированным |

Клинический модуль не зависит от расписания: данные записи и перевод её в `IN_PROGRESS` он получает через свой порт `VisitAppointments`, который реализует `scheduling`.

Несколько приёмов подряд: кнопка «Начать приём» есть у каждой своей записи дня, кроме отменённых, неявок и завершённых, и не прячется, пока открыт другой приём. Открытые приёмы перечислены в меню слева; переход к другому пациенту не закрывает и не подписывает предыдущий. «Завершить приём» на странице визита ставит записи `COMPLETED` — в расписании сразу «Завершён», и кнопки старта у этой строки больше нет.

**Подписанные документы** (страница для `DOCTOR` и `MANAGER`; администратору и кассиру — `403`, пункта меню нет):

| Маршрут | Что делает |
|---|---|
| `GET /api/v1/clinical/signed-documents[?practitionerId=]` | только `SIGNED_LOCKED`, включая подписанные дополнения; черновиков нет. Врач видит документы, которые подписал сам; менеджер — все документы клиники, фильтр по врачу |
| `GET /api/v1/clinical/signed-documents/{id}` | карточка: пациент, врач, время подписи, шаблон, ссылка на исходный документ для дополнения, жалобы, психический статус, диагноз, рекомендации |

Текст берётся из подписанного снимка документа, а не из текущей заметки. Связь с лечением для архива не требуется: доступ даёт роль и факт подписи. Только чтение: подписать или изменить отсюда ничего нельзя. Чтение пишется в аудит (`SIGNED_DOCUMENT_READ`, основание `list` или `card`) без текста документа. В аналитику и кассу эти данные не попадают.

### Касса (`billing`)

| Маршрут | Кто | Что делает |
|---|---|---|
| `GET /api/v1/billing/charges` | `CASHIER`, `ADMINISTRATOR` | счета с оплатой: `appointmentId`, сумма, статус, `paymentStatus` |
| `POST /api/v1/billing/charges` | `CASHIER`, `ADMINISTRATOR` | счёт по приёму, если его ещё нет (`409 CHARGE_ALREADY_EXISTS`) |
| `POST /api/v1/billing/charges/{id}/payments` | `CASHIER` | оплата `{idempotencyKey}`; повтор с тем же ключом не списывает второй раз |

Платёжный шлюз не подключён: оплата только отмечается в кассе со статусом `paymentStatus: MOCK_NO_PROVIDER` — это не настоящий платёж, деньги через систему не проводятся. Чек ставится в outbox (`FISCALIZATION`) и уходит в онлайн-кассу, только когда задан `CLINIC_CASHBOX_URL`; до этого `fiscalStatus` остаётся `QUEUED`, после передачи — `SENT_TO_CASHBOX`.

Один счёт на приём — уникальный индекс по `appointment_id`. Экран «Касса» (кассир и администратор): завершённые приёмы за 90 дней — пациент, врач, услуга, сумма, статус счёта; без диагнозов и текста приёма. Администратор выставляет недостающий счёт, кассир принимает оплату. На экран аналитики деньги не выводятся.

### Аналитика и аудит

- **Аналитика**: `GET /api/v1/analytics/appointments?from=&to=[&practitionerId=]` для `MANAGER`/`ADMINISTRATOR` — агрегаты «дата × врач × статус» без персональных данных.
- **Журнал аудита**: `GET /api/v1/audit[?result=DENIED&objectType=PATIENT]` для `MANAGER`/`ADMINISTRATOR`. Колонка «Основание» — короткий машинный код без клинического текста, телефонов, ФИО, текста заметок и значений полей; интерфейс переводит его на русский (`list:12` → «весь список, 12» и `search:3` → «поиск, найдено 3» — одно событие `PATIENT_LIST_READ` на просмотр списка пациентов, а не по событию на каждого; `card` → «из карточки», `fullName,phone` → «изменены: ФИО, телефон», `BOOKED->CONFIRMED` → «Ожидает → Подтверждён», перенос → «20.10.2026 09:00 → 22.10.2026 10:30», `with-patient` → «по пациенту», `without-patient` → «без пациента», `call-back` → «перезвонить» (старые события), `days:1,4` → «рабочие дни: пн, чт», `role:CASHIER` → «роль: кассир», `self` → «сам сотрудник»). Коды, которые повторяют действие, показываются как «—», неизвестные — как есть.

## Внешние выводы: сообщения пациентам и чеки

Всё, что уходит из CRM наружу, проходит через одну очередь — таблицу `outbox_message` — и доставщик (`communications.OutboxDispatcher`). Второй очереди нет.

**Что ставится в очередь.** Расписание сообщает о событии, а что и когда отправить, решает модуль `communications`:

| Событие | Сообщения |
|---|---|
| Запись создана | подтверждение сразу; если запись на будущий день — напоминание за 24 часа до приёма |
| Запись отменена | сообщение об отмене; неотправленные подтверждение и напоминание отменяются |
| Запись перенесена | сам перенос в очередь ничего не кладёт. Доставщик видит, что время записи сменилось, отменяет неотправленные подтверждение и напоминание на старое время и ставит новые на новое |
| Неявка | неотправленное напоминание отменяется |
| Оплата отмечена | чек для онлайн-кассы |

**Что лежит в базе.** В строке outbox — только шаблон, id записи или платежа, время записи, ключ идемпотентности, канал, статус и машинный код причины. Ни текста сообщения, ни ФИО, ни телефона, ни chat id, ни жалоб, диагноза, заметки или снимка документа. Текст собирается из записи в момент доставки.

**Что уходит пациенту.** Только нейтральный шаблон (`backend/app/src/main/resources/patient-notices.properties`): подтверждение, напоминание или отмена; поля — название клиники, услуга, врач, дата и время. Например: «Напоминаем о записи в AlphaCRM: Повторный приём, врач Александр Петров, 03.11.2026 в 09:00.» Без действующего согласия пациента не отправляется ничего (статус `SKIPPED`, причина `NO_CONSENT`).

**Куда.** Решает порт `NotificationPort`, не контроллер записи:

1. Задан `CLINIC_OUTBOUND_WEBHOOK_URL` — `POST` на него. Так клиника подключает своего бота или SMS-шлюз, не встраивая их в CRM.
2. Иначе задан `TELEGRAM_BOT_TOKEN` и у пациента есть контакт Telegram — тот же текст уходит в Telegram Bot API (`sendMessage`). Телефона недостаточно: chat id пациент получает, только начав диалог с ботом клиники. Администратор вводит chat id в карточке пациента («Регистратура» → «Изменить» → «Telegram chat id»).
3. Иначе — безопасный mock: в лог пишутся шаблон и пометка, что адрес скрыт; сообщение остаётся `PENDING` с причиной `CHANNEL_NOT_CONFIGURED` и уйдёт, когда канал настроят.

**Тело вебхука** (JSON, UTF-8):

```json
{"idempotencyKey":"appointment-reminder:<appointmentId>:<epoch>","template":"appointment-reminder",
 "appointmentId":"…","clinic":"AlphaCRM","service":"Повторный приём","practitioner":"Александр Петров",
 "startsAt":"2026-11-03T09:00:00Z","date":"2026-11-03","time":"09:00","text":"Напоминаем о записи…",
 "recipient":{"patientId":"…","telegramChatId":"123456789"}}
```

Заголовки: `Idempotency-Key` (тот же, что в теле), `X-AlphaCRM-Timestamp` (секунды Unix), `X-AlphaCRM-Signature: sha256=<hex>` — HMAC-SHA256 секретом `CLINIC_OUTBOUND_WEBHOOK_SECRET` от строки `<timestamp>.<тело>`. Получатель проверяет подпись, отклоняет старые timestamp и не отправляет второй раз сообщение с уже виденным `Idempotency-Key`. Телефон пациента в вебхук не уходит; из адресов — только `telegramChatId`, если он есть.

**Онлайн-касса.** Чек (`FISCALIZATION`) в Telegram и вебхук пациента не уходит: для него отдельный порт `FiscalPort`. При заданном `CLINIC_CASHBOX_URL` — `POST {idempotencyKey, operation: "receipt", paymentId, chargeId, amountMinor, currency, paidAt}` с теми же заголовками подписи (секрет `CLINIC_CASHBOX_SECRET`). Без ФИО, контактов и данных приёма.

**Доставщик.** Каждые 30 секунд берёт `PENDING` и `FAILED`, чьё время пришло: помечает попытку (`SENDING`), отправляет и ставит `SENT`. Сетевая ошибка или ответ 5xx/429 — `FAILED` и повтор через 1, 5, 15 минут, 1 и 6 часов (до 5 попыток); 4xx — `FAILED` без повтора. Повтор уходит с тем же ключом идемпотентности, а `SENT`-сообщение второй раз не берётся. Если процесс остановили посреди отправки, через 10 минут вебхук повторяется с тем же ключом, а Telegram — нет (у Bot API нет ключа идемпотентности, второе сообщение пациенту хуже пропущенного).

**Аудит.** `NOTIFICATION_DELIVERED` и `FISCAL_RECEIPT_SENT` — от имени клиники, объект — id строки outbox, основание — `шаблон:канал` (`appointment-reminder:telegram`). Без текста сообщения и без телефона.

**Экран «Внешние выводы»** (только администратор; врач, кассир и менеджер его не видят): последние 100 строк outbox своей клиники — шаблон, запись (время и короткий id) или платёж, канал, статус с причиной, время создания и попыток. Текста сообщения и контакта на экране нет.

| Маршрут | Кто | Что делает |
|---|---|---|
| `GET /api/v1/outbox` | `ADMINISTRATOR` | последние 100 внешних выводов клиники |

Секреты (`CLINIC_OUTBOUND_WEBHOOK_SECRET`, `TELEGRAM_BOT_TOKEN`, `CLINIC_CASHBOX_SECRET`) — только из окружения (`.env`), не из Git и не из Liquibase. Токен Telegram входит в адрес запроса, поэтому адреса запросов наружу не логируются. В профиле `prod` приложение не стартует, если адрес вебхука или кассы задан, а секрет короче 32 символов.

## Архитектура

Подробно — [docs/architecture.md](docs/architecture.md) (модули, путь запроса, безопасность, frontend, правила языка в коде) и [docs/data-model.md](docs/data-model.md) (все таблицы, связи, ограничения, шифрование, аудит, миграции).

Язык в коде: идентификаторы Java, TypeScript и SQL, ключи конфигурации, сообщения логов и `message` в ответах API — на английском; комментарии могут быть русскими; русский текст на экране живёт только в интерфейсе; ФИО, названия услуг и задач — данные и не переводятся.

- Maven reactor с backend-приложением `backend/app`: Spring Boot 4.1.1, Spring Modulith 2.1.1, JPA, Liquibase, PostgreSQL 18;
- модули: staff (клиники, вход и учётные записи), patients, scheduling (включая аналитику), registry (задачи и лист ожидания), clinical/documents, billing, communications/outbox, audit и platform; границы проверяет `ModularityTest`. `staff` и `registry` не зависят от кода других модулей: существование пациента, врача или сотрудника проверяется запросом к таблице;
- сессии Spring Security с cookie `HttpOnly`/`SameSite=Lax`, bcrypt-хеши паролей, CSRF через cookie `XSRF-TOKEN`;
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
  app/          App.tsx (вход, смена временного пароля или рабочее место), useSession.ts (сессия, песочница),
                Workspace.tsx (каркас и навигация), Sidebar, Topbar, theme.tsx,
                useClinicData.ts (данные и действия: сервер или песочница), demoData.ts
  api/          client.ts (apiFetch: cookie, CSRF, 401 → вход; ошибки), errorMessages.ts (код ошибки →
                фраза на экране), auth.ts, staff.ts,
                registry.ts, schedule.ts, patients.ts, clinical.ts, billing.ts, analytics.ts,
                audit.ts, types.ts
  features/
    auth/       LoginPage.tsx, ForcePasswordPage.tsx (смена временного пароля), PasswordDialog.tsx,
                password.ts
    documents/  SignedDocuments.tsx, SignedDocumentDialog.tsx, signedDocuments.ts
    staff/      StaffPage.tsx, StaffDialog.tsx, staffRules.ts
    registry/   RegistryPage.tsx, TaskList.tsx, TaskDialog.tsx, WaitlistDialog.tsx,
                WaitlistMatchesDialog.tsx, useRegistry.ts, registry.ts
    settings/   SettingsPage.tsx, WorkingHoursForm.tsx, PriceForm.tsx (цена, выключение услуги),
                ServiceForm.tsx (новая услуга), serviceDraft.ts, shifts.ts
    schedule/   Schedule.tsx (день и неделя), AppointmentRow.tsx, BookingDialog.tsx,
                RescheduleDialog.tsx, booking.ts, statusActions.ts, workingHours.ts
    patients/   PatientsList.tsx, PatientCard.tsx, RegistryPanel.tsx, RegistryDialog.tsx,
                PatientDialog.tsx, ConsentDialog.tsx, consentLabels.ts, history.ts, usePatientHistory.ts,
                useClinicalHistory.ts, search.ts
    visit/      Visit.tsx, SignDialog.tsx, useVisit.ts, visit.ts
    cashier/    Cashier.tsx, cashier.ts
    analytics/  Analytics.tsx, stats.ts
    audit/      AuditLog.tsx, auditLabels.ts
  shared/       dates.ts, roles.ts (коды ролей и их названия), sandbox.ts (память демо-песочницы),
                storage.ts (ключи localStorage alphacrm-* с чтением прежних quiet-clinic-*), text.ts,
                ui.tsx (Metric, Info, FormSection)
  test/         setup.ts (общая подготовка), helpers.tsx (renderApp, withAuth — сервер с сессией,
                signIn, enterDemo — песочница по роли, chooseRole — выйти и войти другим сотрудником, goOffline)
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
| `AppointmentStatusPolicyTest`, `WorkingHoursPolicyTest`, `RescheduleReasonTest`, `AppointmentStatsServiceTest`, `PatientTest`, `PhoneIndexTest`, `WaitlistEntryTest`, `MedicalDocumentTest`, `NoteContentTest`, `ProductionReadinessCheckTest`, `ClinicBootstrapTest` | правила и расчёты | нет, миллисекунды |
| `AuthControllerTest`, `StaffControllerTest`, `SignedDocumentControllerTest`, `SchedulingControllerTest`, `ServiceCatalogControllerTest`, `AnalyticsControllerTest`, `PatientsControllerTest`, `RegistryControllerTest`, `ClinicalControllerTest`, `BillingControllerTest`, `AuditControllerTest` | HTTP-сценарии на H2: вход через `/auth/login` как `admin` / `admin`, остальных сотрудников помощник `StaffSessions` заводит через API сотрудников; вторая клиника — `SecondClinic`; сессия, CSRF и права ролей | да |
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

**Backend не стартует: «Задайте CLINIC_ADMIN_PASSWORD» или «…не короче 12 символов».** В `.env` нет пароля начального администратора или он слабый. Запустите `./scripts/start.sh` / `.\scripts\start.ps1` — скрипт допишет его в `.env` и напечатает — или впишите сами.

**Не получается войти: «Неверный логин или пароль».** В Docker начальный вход — `admin` и пароль из `CLINIC_ADMIN_PASSWORD` в `.env` (он действует, только пока администратор создаётся на пустой базе; после смены пароля — новый). Прежние логины `smirnova`, `petrov` и т. д. удалены миграцией. Если администратор забыл свой пароль, а другого администратора нет, задайте новый bcrypt-хеш прямо в базе (`UPDATE staff_user SET password_hash = '<bcrypt>', must_change_password = true WHERE username = 'admin'`).

**На странице входа «Сервер недоступен» и кнопки «Песочница: …».** Backend недоступен: проверьте `docker compose ps` и `docker compose logs backend`. При разработке — `http://127.0.0.1:8081/actuator/health`.

**Смотреть логи и состояние:**

```bash
docker compose ps -a
docker compose logs --tail 100 backend
docker compose logs --tail 100 postgres
```
