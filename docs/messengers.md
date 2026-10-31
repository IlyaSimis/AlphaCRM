# Telegram и MAX

## Топология

```text
пациент → Telegram → зарубежный VPS: alphacrm-telegram-bot
                           │ WireGuard 10.77.0.2 → 10.77.0.1
                           ▼
                    МИС в РФ + PostgreSQL
                           ▲
пациент → MAX → alphacrm-max-bot (РФ, внутренняя Docker-сеть)
```

Telegram Bot API вызывается только зарубежным сервисом. МИС передаёт ему нейтральные уведомления через `POST /internal/send` с HMAC-SHA256, timestamp и idempotency key. MAX Bot API вызывается непосредственно МИС в РФ. Публичный nginx возвращает 404 для `/api/v1/bot/**` и `/api/v1/messenger/**`.

## 1. Подготовка

- Зарегистрируйте Telegram-бота через BotFather и MAX-бота через официальный кабинет MAX. Это требует аккаунтов владельца и не выполняется автоматически.
- Выделите HTTPS-домены `tg-bot.example.com` и `max-bot.clinic.example.ru`.
- Сгенерируйте независимые секреты длиной не менее 32 байт: Telegram webhook, Telegram relay, MAX webhook, сервисный API МИС.
- В МИС задайте публичные адреса `CLINIC_MESSENGER_TELEGRAM_PUBLIC_URL=https://t.me/<name>` и `CLINIC_MESSENGER_MAX_PUBLIC_URL=https://max.ru/<name>`.

## 2. WireGuard

Примеры находятся в `deploy/wireguard/`. На обоих серверах:

```bash
umask 077
wg genkey | tee privatekey | wg pubkey > publickey
```

Подставьте ключи в конфиги, разрешите UDP 51820 на VPS и при необходимости на МИС. Запустите `sudo systemctl enable --now wg-quick@wg0`. Проверьте `ping 10.77.0.1` с VPS и `ping 10.77.0.2` с МИС. Firewall должен разрешать только нужные соединения: VPS → МИС TCP 8081 и МИС → VPS TCP 8081. Backend не публикуется в интернет.

## 3. Telegram на зарубежном VPS

Скопируйте отдельный репозиторий `../alphacrm-telegram-bot`, создайте `.env` из примера. Укажите `CRM_API_URL=http://10.77.0.1:8081/api/v1/messenger`, `PUBLIC_URL`, токены и секреты. Caddy-пример — `deploy/messengers/telegram.Caddyfile.example`.

```bash
docker compose up -d --build
docker compose ps
curl http://127.0.0.1:8080/health
```

Порт 8080 доступен только Caddy, порт 8081 — только адресу WireGuard. В МИС задайте `CLINIC_OUTBOUND_TELEGRAM_RELAY_URL=http://10.77.0.2:8081` и общий `CLINIC_OUTBOUND_TELEGRAM_RELAY_SECRET`.

## 4. MAX и МИС в РФ

В корневом `.env` задайте `CLINIC_OUTBOUND_MAX_BOT_TOKEN`, `MAX_WEBHOOK_SECRET`, `MAX_BOT_PUBLIC_URL`, `CLINIC_BOT_API_TOKEN`. Caddy-пример — `deploy/messengers/max.Caddyfile.example`.

```bash
docker compose --profile max-bot up -d --build
curl http://127.0.0.1:8082/health
```

Привязка пациента: в карточке сотрудник создаёт ссылку, пациент открывает её в течение 10 минут. МИС погашает токен один раз. Уведомление идёт в предпочтительный канал; если он недоступен — во второй подключённый. Одновременная отправка в оба канала по умолчанию не производится.

## 5. Боевая приёмка

1. Привязать тестовую карточку отдельно в Telegram и MAX; повтор старой ссылки должен быть отклонён.
2. Создать запись через каждого бота и убедиться, что существующий пациент не дублируется.
3. Проверить `/appointments`, подтверждение и отмену; чужой callback не должен открывать запись.
4. Проверить outbox: канал, попытка, доставка и отсутствие текста/адреса в журнале.
5. Разорвать WireGuard: Telegram-уведомление должно остаться в outbox и повториться с тем же idempotency key после восстановления.
