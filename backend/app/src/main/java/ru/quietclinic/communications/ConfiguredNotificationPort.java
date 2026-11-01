package ru.quietclinic.communications;

import org.springframework.stereotype.Component;

/**
 * Выбор канала: предпочтительный мессенджер пациента, затем второй подключённый, вебхук клиники или
 * безопасный mock. Настроенность канала проверяется вместе с наличием внешнего id.
 */
@Component
class ConfiguredNotificationPort implements NotificationPort {
  private final WebhookChannel webhook;
  private final TelegramChannel legacyTelegram;
  private final MaxChannel legacyMax;
  private final SafeMockNotificationAdapter mock;
  private final MessengerChannelFactory scoped;

  @org.springframework.beans.factory.annotation.Autowired
  ConfiguredNotificationPort(
      WebhookChannel webhook,
      TelegramChannel telegram,
      MaxChannel max,
      SafeMockNotificationAdapter mock,
      MessengerChannelFactory scoped) {
    this.webhook = webhook;
    this.legacyTelegram = telegram;
    this.legacyMax = max;
    this.mock = mock;
    this.scoped = scoped;
  }

  ConfiguredNotificationPort(
      WebhookChannel webhook,
      TelegramChannel telegram,
      MaxChannel max,
      SafeMockNotificationAdapter mock) {
    this(webhook, telegram, max, mock, null);
  }

  /** Совместимость модульных тестов и старых конфигураций без MAX. */
  ConfiguredNotificationPort(
      WebhookChannel webhook, TelegramChannel telegram, SafeMockNotificationAdapter mock) {
    this(webhook, telegram, null, mock, null);
  }

  @Override
  public String channelFor(PatientMessage message) {
    var preferred = message.preferredMessenger();
    var telegram = telegramFor(message);
    var max = maxFor(message);
    if (MaxChannel.NAME.equals(preferred)
        && present(message.maxUserId())
        && max != null
        && max.configured()) return MaxChannel.NAME;
    if (TelegramChannel.NAME.equals(preferred)
        && present(message.telegramChatId())
        && telegram.configured()) {
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
      case TelegramChannel.NAME -> telegramFor(message).send(message);
      case MaxChannel.NAME -> {
        var max = maxFor(message);
        yield max == null
            ? DeliveryResult.notConfigured(MaxChannel.NAME, "CHANNEL_NOT_CONFIGURED")
            : max.send(message);
      }
      case WebhookChannel.NAME -> webhook.send(message);
      default -> mock.send(message);
    };
  }

  private TelegramChannel telegramFor(PatientMessage message) {
    if (scoped == null || message.clinicId() == null) return legacyTelegram;
    return scoped.telegram(message.clinicId()).orElse(legacyTelegram);
  }

  private MaxChannel maxFor(PatientMessage message) {
    if (scoped == null || message.clinicId() == null) return legacyMax;
    return scoped.max(message.clinicId()).orElse(legacyMax);
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }
}
