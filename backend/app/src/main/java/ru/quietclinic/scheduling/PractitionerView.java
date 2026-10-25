package ru.quietclinic.scheduling;

import java.util.List;
import java.util.UUID;

/** Врач клиники; room — кабинет или null. */
record PractitionerView(
    UUID id, String fullName, String specialty, String room, List<WorkingHoursView> workingHours) {}
