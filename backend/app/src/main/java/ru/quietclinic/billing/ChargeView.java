package ru.quietclinic.billing;

import java.time.Instant;
import java.util.UUID;

/** Строка кассы: счёт по приёму и его оплата. Ни диагнозов, ни текста приёма. */
record ChargeView(
    UUID chargeId,
    UUID appointmentId,
    long amountMinor,
    String currency,
    String status,
    Instant createdAt,
    UUID paymentId,
    String paymentStatus,
    String fiscalStatus) {

  static ChargeView of(Charge charge, PaymentRecord payment) {
    return new ChargeView(
        charge.id,
        charge.appointmentId,
        charge.amountMinor,
        charge.currency,
        charge.status,
        charge.createdAt,
        payment == null ? null : payment.id,
        payment == null ? null : payment.paymentStatus,
        payment == null ? null : payment.fiscalStatus);
  }
}
