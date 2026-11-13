package ru.quietclinic.patients;

import jakarta.validation.constraints.Size;

/** Законный представитель пациента; основной используется для связи в первую очередь. */
record PatientRepresentative(
    @Size(max = 255) String fullName, @Size(max = 120) String relationship, Boolean primary) {}
