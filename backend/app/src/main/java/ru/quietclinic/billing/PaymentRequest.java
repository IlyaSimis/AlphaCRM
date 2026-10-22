package ru.quietclinic.billing;

import jakarta.validation.constraints.NotBlank;

record PaymentRequest(@NotBlank String idempotencyKey) {}
