package ru.quietclinic.patients;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ConsentRequest(
    @NotBlank @Size(max = 80) String version, @NotBlank @Size(max = 120) String basis) {}
