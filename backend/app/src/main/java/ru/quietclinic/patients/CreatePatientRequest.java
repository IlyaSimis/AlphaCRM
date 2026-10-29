package ru.quietclinic.patients;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Новый пациент с первым согласием. Те же ограничения полей, что при правке карточки. */
record CreatePatientRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Size(max = 255) String phone,
    @Size(max = 255) @Pattern(regexp = PatientRules.EMAIL) String email,
    @Size(max = 255) String secondPhone,
    UUID assignedStaffId,
    @Valid PatientProfile profile,
    @NotBlank @Size(max = 80) String consentVersion,
    @NotBlank @Size(max = 120) String consentBasis) {

  PatientDetails details() {
    return new PatientDetails(
        fullName, birthDate, phone, email, secondPhone, assignedStaffId, profile);
  }
}
