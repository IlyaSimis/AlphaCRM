package ru.quietclinic.billing;

import jakarta.validation.constraints.Positive;

/** Сумма свободной цены в копейках. */
record ChargeAmountRequest(@Positive long amountMinor) {}
