package ru.quietclinic.billing;

import java.util.UUID;

record BillingView(
    UUID chargeId,
    Long amountMinor,
    String currency,
    String chargeStatus,
    UUID paymentId,
    String paymentStatus,
    String fiscalStatus,
    String paymentMethod,
    String failureReason) {}
