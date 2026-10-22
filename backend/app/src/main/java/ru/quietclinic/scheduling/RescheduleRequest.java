package ru.quietclinic.scheduling;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

record RescheduleRequest(
    @NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull UUID practitionerId) {}
