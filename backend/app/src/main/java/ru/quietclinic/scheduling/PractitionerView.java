package ru.quietclinic.scheduling;

import java.util.List;
import java.util.UUID;

record PractitionerView(
    UUID id, String fullName, String specialty, List<WorkingHoursView> workingHours) {}
