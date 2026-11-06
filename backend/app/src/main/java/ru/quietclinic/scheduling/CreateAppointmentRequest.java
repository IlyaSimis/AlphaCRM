package ru.quietclinic.scheduling;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;

/** estimateMinor — необязательная оценка суммы для услуги со свободной ценой. */
record CreateAppointmentRequest(
    @NotNull UUID patientId,
    @NotNull UUID practitionerId,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotNull String serviceName,
    @PositiveOrZero Long estimateMinor,
    @Pattern(regexp = "^[0-9]{1,64}$") String metricaClientId) {}
