package ru.quietclinic.communications;

import java.time.Instant;
import java.util.UUID;

/**
 * Сохраняемый payload: только шаблон, ссылка на запись или платёж и время записи. Ни текста
 * сообщения, ни ФИО, ни контакта, ни клинических данных — их здесь неоткуда взять.
 */
final class Payloads {
  private Payloads() {}

  static String of(String template, String aggregateType, UUID aggregateId, Instant startsAt) {
    String ref = "APPOINTMENT".equals(aggregateType) ? "appointmentId" : "paymentId";
    return "{\"template\":"
        + Json.quote(template)
        + ",\""
        + ref
        + "\":"
        + Json.quote(aggregateId.toString())
        + (startsAt == null ? "" : ",\"startsAt\":" + Json.quote(startsAt.toString()))
        + "}";
  }
}
