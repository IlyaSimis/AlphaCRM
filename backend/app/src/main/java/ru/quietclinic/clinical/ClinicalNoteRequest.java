package ru.quietclinic.clinical;

import jakarta.validation.constraints.NotBlank;

record ClinicalNoteRequest(
    @NotBlank String complaints,
    @NotBlank String mentalStatus,
    @NotBlank String diagnosis,
    @NotBlank String recommendations) {}
