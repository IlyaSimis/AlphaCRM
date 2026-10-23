package ru.quietclinic.registry;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

record CreateWaitlistRequest(
    @NotNull UUID patientId,
    @NotNull UUID practitionerId,
    @NotNull UUID serviceId,
    @NotNull LocalDate wishFrom,
    @NotNull LocalDate wishTo) {}
