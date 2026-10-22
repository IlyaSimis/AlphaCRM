package ru.quietclinic.scheduling;

import java.time.LocalDate;
import java.util.UUID;

/** Параметры выборки записей: день или период и необязательные фильтры. */
record AppointmentQuery(
    LocalDate date,
    LocalDate from,
    LocalDate to,
    UUID patientId,
    UUID practitionerId,
    String status) {}
