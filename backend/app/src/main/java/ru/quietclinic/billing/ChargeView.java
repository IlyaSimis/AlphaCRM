package ru.quietclinic.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Строка кассы: счёт по приёму, его оплата и возврат. Ни диагнозов, ни текста приёма. При свободной
 * цене (priceMode = OPEN) до ввода суммы amountMinor пуст, статус AWAITING_AMOUNT. Аннулированный
 * счёт — VOIDED с кодом причины; возвращённый — REFUNDED со строкой возврата.
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
    String paymentMethod,
    String failureReason,
    String priceMode,
    Long minMinor,
    Long maxMinor,
    Long estimateMinor,
    Instant paidAt,
    String voidReason,
    Instant voidedAt,
    UUID refundId,
    String refundReason,
    String refundFiscalStatus,
    Instant refundedAt) {

  static ChargeView of(Charge charge, PaymentRecord payment) {
    return of(charge, payment, null);
  }

  static ChargeView of(Charge charge, PaymentRecord payment, RefundRecord refund) {
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
        payment == null ? null : payment.method,
        payment == null ? null : payment.failureReason,
        charge.priceMode,
        charge.minMinor,
        charge.maxMinor,
        charge.estimateMinor,
        payment == null
            ? null
            : payment.completedAt != null ? payment.completedAt : payment.createdAt,
        charge.voidReason,
        charge.voidedAt,
        refund == null ? null : refund.id,
        refund == null ? null : refund.reason,
        refund == null ? null : refund.fiscalStatus,
        refund == null ? null : refund.createdAt);
  }
}
