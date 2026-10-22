package ru.quietclinic.clinical;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record StartEncounterRequest(
    @NotNull UUID appointmentId, @NotNull UUID patientId, @NotNull UUID practitionerId) {}
