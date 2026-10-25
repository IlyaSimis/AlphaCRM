package ru.quietclinic.communications;

import java.time.Instant;
import java.util.UUID;

/**
 * Строка экрана «Внешние выводы»: шаблон, запись или платёж, канал, статус и время. Текста
 * сообщения и контакта нет — их нет и в базе.
 */
record OutboxView(
    UUID id,
    String topic,
    String template,
    String aggregateType,
    UUID aggregateId,
    Instant appointmentStartsAt,
    String channel,
    String status,
    String reason,
    int attempts,
    Instant createdAt,
    Instant lastAttemptAt,
    Instant nextAttemptAt,
    Instant sentAt) {

  static OutboxView of(OutboxMessage m) {
    return new OutboxView(
        m.id,
        m.topic,
        m.template,
        m.aggregateType,
        m.aggregateId,
        m.appointmentStartsAt,
        m.channel,
        m.status,
        m.lastError,
        m.attempts,
        m.createdAt,
        m.lastAttemptAt,
        m.nextAttemptAt,
        m.sentAt);
  }
}
