package ru.quietclinic.patients;

import java.time.LocalDate;
import java.util.UUID;

/** Поля карточки, общие для регистрации и правки. */
record PatientDetails(
    String fullName,
    LocalDate birthDate,
    String phone,
    String email,
    String secondPhone,
    UUID assignedStaffId) {}
