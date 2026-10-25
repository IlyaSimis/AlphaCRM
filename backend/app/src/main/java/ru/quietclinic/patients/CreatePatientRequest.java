package ru.quietclinic.patients;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Новый пациент с первым согласием. Те же ограничения полей, что при правке карточки. */
record CreatePatientRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Size(max = 255) String phone,
    @NotBlank @Size(max = 80) String consentVersion,
    @NotBlank @Size(max = 120) String consentBasis) {}
