package ru.quietclinic.patients;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record UpdatePatientRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Size(max = 255) String phone) {}
