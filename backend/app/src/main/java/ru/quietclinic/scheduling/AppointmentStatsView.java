package ru.quietclinic.scheduling;

import java.time.LocalDate;
import java.util.List;

record AppointmentStatsView(LocalDate from, LocalDate to, List<AppointmentStatRow> rows) {}
