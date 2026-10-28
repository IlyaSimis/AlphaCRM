package ru.quietclinic.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Возврат оплаты: полный, один на платёж. Чек возврата уходит через порт кассы (outbox). */
@Entity
@Table(name = "refund_record")
class RefundRecord {
  @Id UUID id;

  @Column(nullable = false, unique = true)
  UUID paymentId;

  @Column(nullable = false)
  UUID chargeId;

  @Column(nullable = false, unique = true)
  String idempotencyKey;

  @Column(nullable = false)
  long amountMinor;

  @Column(nullable = false)
  String currency;

  /** Машинный код причины, без свободного текста. */
  @Column(nullable = false)
  String reason;

  @Column(nullable = false)
  String fiscalStatus;

  @Column(nullable = false)
  UUID createdBy;

  @Column(nullable = false)
  Instant createdAt;

  protected RefundRecord() {}

  RefundRecord(PaymentRecord payment, Charge charge, String key, String reason, UUID actor) {
    id = UUID.randomUUID();
    paymentId = payment.id;
    chargeId = charge.id;
    idempotencyKey = key;
    amountMinor = charge.amountMinor;
    currency = charge.currency;
    this.reason = reason;
    fiscalStatus = "QUEUED";
    createdBy = actor;
    createdAt = Instant.now();
  }
}
