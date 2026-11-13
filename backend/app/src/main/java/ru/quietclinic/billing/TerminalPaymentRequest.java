package ru.quietclinic.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record TerminalPaymentRequest(
    @NotBlank @Size(max = 128) String idempotencyKey, @NotNull TerminalPaymentMethod method) {}
