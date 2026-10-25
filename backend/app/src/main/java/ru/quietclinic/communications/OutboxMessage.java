package ru.quietclinic.communications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Сообщение в outbox. Хранит только машинные поля: шаблон, ключ идемпотентности, время записи и
 * итог попытки. Текст для пациента, телефон и chat id здесь не хранятся: текст собирается из
 * записи при доставке, адрес выбирает порт.
 */
@Entity
@Table(name = "outbox_message")
class OutboxMessage {
  @Id UUID id;

  @Column(nullable = false)
  String topic;

  @Column(nullable = false)
  String aggregateType;

  @Column(nullable = false)
  UUID aggregateId;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String template;

  /** Один ключ — одно сообщение получателю: повтор попытки уходит с тем же ключом. */
  @Column(nullable = false, unique = true)
  String idempotencyKey;

  /** Время записи, о котором сообщение; после переноса старое сообщение не отправляется. */
  Instant appointmentStartsAt;

  @Column(nullable = false, length = 4000)
  String payload;

  /** PENDING, SENDING, SENT, FAILED, CANCELLED, SKIPPED. */
  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  Instant createdAt;

  /** Не раньше этого момента: напоминание — за сутки до приёма, повтор — после паузы. */
  Instant nextAttemptAt;

  Instant lastAttemptAt;

  Instant sentAt;

  /** Неудачные попытки; при успехе и «канал не настроен» не растёт. */
  @Column(nullable = false)
  int attempts;

  /** WEBHOOK, TELEGRAM, CASHBOX, MOCK — куда ушла (или уходила) последняя попытка. */
  String channel;

  /** Машинный код причины: CHANNEL_NOT_CONFIGURED, NO_CONSENT, RESCHEDULED, HTTP_503… */
  String lastError;

  @Version long version;

  protected OutboxMessage() {}

  OutboxMessage(
      String topic,
      String aggregateType,
      UUID aggregateId,
      UUID clinicId,
      String template,
      String idempotencyKey,
      Instant appointmentStartsAt,
      String payload,
      Instant notBefore) {
    id = UUID.randomUUID();
    this.topic = topic;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.clinicId = clinicId;
    this.template = template;
    this.idempotencyKey = idempotencyKey;
    this.appointmentStartsAt = appointmentStartsAt;
    this.payload = payload;
    status = "PENDING";
    createdAt = Instant.now();
    nextAttemptAt = notBefore;
  }
}
