package ru.quietclinic.communications;

import org.springframework.stereotype.Component;

/**
 * Выбор канала: предпочтительный мессенджер пациента, затем второй подключённый, вебхук клиники
 * или безопасный mock. Настроенность канала проверяется вместе с наличием внешнего id.
 */
@Component
class ConfiguredNotificationPort implements NotificationPort {
  private final WebhookChannel webhook;
  private final TelegramChannel telegram;
  private final MaxChannel max;
  private final SafeMockNotificationAdapter mock;

  @org.springframework.beans.factory.annotation.Autowired
  ConfiguredNotificationPort(
      WebhookChannel webhook, TelegramChannel telegram, MaxChannel max, SafeMockNotificationAdapter mock) {
    this.webhook = webhook;
    this.telegram = telegram;
    this.max = max;
    this.mock = mock;
  }

  /** Совместимость модульных тестов и старых конфигураций без MAX. */
  ConfiguredNotificationPort(
      WebhookChannel webhook, TelegramChannel telegram, SafeMockNotificationAdapter mock) {
    this(webhook, telegram, null, mock);
  }

  @Override
  public String channelFor(PatientMessage message) {
    var preferred = message.preferredMessenger();
    if (MaxChannel.NAME.equals(preferred) && present(message.maxUserId()) && max != null && max.configured()) return MaxChannel.NAME;
    if (TelegramChannel.NAME.equals(preferred) && present(message.telegramChatId()) && telegram.configured()) {
      return TelegramChannel.NAME;
    }
    if (present(message.telegramChatId()) && telegram.configured()) return TelegramChannel.NAME;
    if (present(message.maxUserId()) && max != null && max.configured()) return MaxChannel.NAME;
    if (webhook.configured()) return WebhookChannel.NAME;
    return SafeMockNotificationAdapter.NAME;
  }

  @Override
  public DeliveryResult deliver(PatientMessage message) {
    return switch (channelFor(message)) {
      case TelegramChannel.NAME -> telegram.send(message);
      case MaxChannel.NAME -> max == null
          ? DeliveryResult.notConfigured(MaxChannel.NAME, "CHANNEL_NOT_CONFIGURED") : max.send(message);
      case WebhookChannel.NAME -> webhook.send(message);
      default -> mock.send(message);
    };
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
