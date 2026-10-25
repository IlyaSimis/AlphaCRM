package ru.quietclinic.communications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Безопасный mock, пока ни вебхук, ни Telegram не настроены: в лог — только шаблон и пометка, что
 * адрес скрыт. Ничего не отправляет и доставленным сообщение не считает.
 */
@Component
class SafeMockNotificationAdapter {
  static final String NAME = "MOCK";
  private static final Logger log = LoggerFactory.getLogger(SafeMockNotificationAdapter.class);

  DeliveryResult send(PatientMessage message) {
    log.info(
        "mock_notification template={} key={} destination=redacted channel_not_configured",
        message.template(),
        message.idempotencyKey());
    return DeliveryResult.notConfigured(NAME, "CHANNEL_NOT_CONFIGURED");
  }
}
