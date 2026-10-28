package ru.quietclinic.billing;

import java.time.Instant;
import java.util.UUID;

record RefundView(
    UUID refundId,
    UUID paymentId,
    UUID chargeId,
    long amountMinor,
    String currency,
    String reason,
    String fiscalStatus,
    Instant createdAt) {

  static RefundView of(RefundRecord refund) {
    return new RefundView(
        refund.id,
        refund.paymentId,
        refund.chargeId,
        refund.amountMinor,
        refund.currency,
        refund.reason,
        refund.fiscalStatus,
        refund.createdAt);
  }
}
