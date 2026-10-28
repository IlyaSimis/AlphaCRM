package ru.quietclinic.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Строка кассы: счёт по приёму и его оплата. Ни диагнозов, ни текста приёма. При свободной цене
 * (priceMode = OPEN) до ввода суммы amountMinor пуст, статус AWAITING_AMOUNT.
 */
record ChargeView(
    UUID chargeId,
    UUID appointmentId,
    Long amountMinor,
    String currency,
    String status,
    Instant createdAt,
    UUID paymentId,
    String paymentStatus,
    String fiscalStatus,
    String priceMode,
    Long minMinor,
    Long maxMinor,
    Long estimateMinor) {

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
        payment == null ? null : payment.fiscalStatus,
        charge.priceMode,
        charge.minMinor,
        charge.maxMinor,
        charge.estimateMinor);
  }
}
