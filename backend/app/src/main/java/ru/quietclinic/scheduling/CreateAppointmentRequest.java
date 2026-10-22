package ru.quietclinic.scheduling;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

record CreateAppointmentRequest(
    @NotNull UUID patientId,
    @NotNull UUID practitionerId,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @NotNull String serviceName) {}
