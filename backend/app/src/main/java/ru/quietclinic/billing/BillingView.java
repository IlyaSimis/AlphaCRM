package ru.quietclinic.billing;

import java.util.UUID;

record BillingView(
    UUID chargeId,
    long amountMinor,
    String currency,
    String chargeStatus,
    UUID paymentId,
    String paymentStatus,
    String fiscalStatus) {}
