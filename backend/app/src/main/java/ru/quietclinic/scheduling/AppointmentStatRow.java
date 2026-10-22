package ru.quietclinic.scheduling;

import java.time.LocalDate;
import java.util.UUID;

/** Количество записей за день по врачу и статусу. Даты считаются в UTC, как и расписание. */
record AppointmentStatRow(LocalDate date, UUID practitionerId, String status, long count) {}
