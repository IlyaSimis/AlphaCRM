package ru.quietclinic.patients;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Карточка целиком. Необязательные e-mail, второй телефон и постоянный администратор: пустое или
 * отсутствующее значение убирает поле из карточки.
 */
record UpdatePatientRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Size(max = 255) String phone,
    @Size(max = 255) @Pattern(regexp = PatientRules.EMAIL) String email,
    @Size(max = 255) String secondPhone,
    UUID assignedStaffId) {

  PatientDetails details() {
    return new PatientDetails(fullName, birthDate, phone, email, secondPhone, assignedStaffId);
  }
}
