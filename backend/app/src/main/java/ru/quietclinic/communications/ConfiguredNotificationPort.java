package ru.quietclinic.communications;

import org.springframework.stereotype.Component;

/**
 * Выбор канала по настройкам окружения: вебхук клиники, если задан его адрес; иначе Telegram, если
 * задан токен бота; иначе безопасный mock.
 */
@Component
class ConfiguredNotificationPort implements NotificationPort {
  private final WebhookChannel webhook;
  private final TelegramChannel telegram;
  private final SafeMockNotificationAdapter mock;

  ConfiguredNotificationPort(
      WebhookChannel webhook, TelegramChannel telegram, SafeMockNotificationAdapter mock) {
    this.webhook = webhook;
    this.telegram = telegram;
    this.mock = mock;
  }

  @Override
  public String channelFor(PatientMessage message) {
    if (webhook.configured()) return WebhookChannel.NAME;
    if (telegram.configured()) return TelegramChannel.NAME;
    return SafeMockNotificationAdapter.NAME;
  }

  @Override
  public DeliveryResult deliver(PatientMessage message) {
    if (webhook.configured()) return webhook.send(message);
    if (telegram.configured()) return telegram.send(message);
    return mock.send(message);
  }
}
