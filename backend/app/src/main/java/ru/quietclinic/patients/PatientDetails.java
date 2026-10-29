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
    UUID assignedStaffId,
    PatientProfile profile) {

  /** Старые интеграции и импорт не передают расширенный профиль — он остаётся без изменений. */
  PatientDetails(
      String fullName,
      LocalDate birthDate,
      String phone,
      String email,
      String secondPhone,
      UUID assignedStaffId) {
    this(fullName, birthDate, phone, email, secondPhone, assignedStaffId, null);
  }
}
