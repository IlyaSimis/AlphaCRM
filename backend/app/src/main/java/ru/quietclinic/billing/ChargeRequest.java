package ru.quietclinic.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

record ChargeRequest(
    @NotNull UUID appointmentId, @Positive long amountMinor, @NotBlank String currency) {}
