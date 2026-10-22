package ru.quietclinic.patients;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

record CreatePatientRequest(
    @NotBlank String fullName,
    @NotNull LocalDate birthDate,
    @NotBlank String phone,
    @NotBlank String consentVersion,
    @NotBlank String consentBasis) {}
