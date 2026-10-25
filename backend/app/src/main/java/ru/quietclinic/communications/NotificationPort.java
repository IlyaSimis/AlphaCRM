package ru.quietclinic.communications;

/**
 * Куда и как отправить сообщение пациенту, решает порт, а не контроллер записи. Реализация —
 * {@link ConfiguredNotificationPort}: вебхук клиники, Telegram или безопасный mock.
 */
interface NotificationPort {
  /** Канал, через который уйдёт сообщение: WEBHOOK, TELEGRAM или MOCK. */
  String channelFor(PatientMessage message);

  DeliveryResult deliver(PatientMessage message);
}
