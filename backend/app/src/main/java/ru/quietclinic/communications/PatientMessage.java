package ru.quietclinic.communications;

import java.time.Instant;
import java.util.UUID;

/**
 * Сообщение пациенту, собранное из записи в момент доставки. Только нейтральный шаблон: клиника,
 * услуга, врач, дата и время. Живёт в памяти доставщика и в базу не сохраняется.
 *
 * @param telegramChatId контакт TELEGRAM пациента или null — его видит только канал, не лог
 */
record PatientMessage(
    String idempotencyKey,
    String template,
    UUID appointmentId,
    UUID patientId,
    String clinicName,
    String serviceName,
    String practitionerName,
    Instant startsAt,
    String date,
    String time,
    String text,
    String telegramChatId,
    String maxUserId,
    String preferredMessenger) {

  PatientMessage(
      String idempotencyKey, String template, UUID appointmentId, UUID patientId, String clinicName,
      String serviceName, String practitionerName, Instant startsAt, String date, String time,
      String text, String telegramChatId) {
    this(idempotencyKey, template, appointmentId, patientId, clinicName, serviceName,
        practitionerName, startsAt, date, time, text, telegramChatId, null, null);
  }

  @Override
  public String toString() {
    // В лог — только шаблон и ключ; адрес и текст скрыты.
    return "PatientMessage[template="
        + template
        + ", key="
        + idempotencyKey
        + ", recipient=redacted]";
  }
}
