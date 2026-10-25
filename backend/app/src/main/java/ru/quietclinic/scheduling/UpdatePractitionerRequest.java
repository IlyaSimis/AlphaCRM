package ru.quietclinic.scheduling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Специальность и кабинет врача; пустой кабинет стирает подпись. */
record UpdatePractitionerRequest(
    @NotBlank @Size(max = 120) String specialty, @Size(max = 80) String room) {}
