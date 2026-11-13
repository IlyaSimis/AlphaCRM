package ru.quietclinic.billing;

import java.util.UUID;

/** Команда адаптеру терминала. Персональные данные пациента в неё не входят. */
record TerminalPaymentCommand(
    UUID paymentId,
    UUID chargeId,
    long amountMinor,
    String currency,
    TerminalPaymentMethod method,
    String idempotencyKey) {}
