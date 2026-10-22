package ru.quietclinic.clinical;

import java.util.UUID;

/** То, что клиническому модулю нужно знать о записи, чтобы начать по ней приём. */
public record AppointmentSnapshot(
    UUID appointmentId, UUID patientId, UUID practitionerId, String status) {}
