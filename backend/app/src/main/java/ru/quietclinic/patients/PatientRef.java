package ru.quietclinic.patients;

import java.util.UUID;

/** Другая карточка с тем же телефоном — для предупреждения регистратуре. */
record PatientRef(UUID id, String fullName) {}
