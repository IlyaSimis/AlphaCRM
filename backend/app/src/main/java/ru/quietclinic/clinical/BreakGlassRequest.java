package ru.quietclinic.clinical;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Причина — код из списка и пояснение; пароль владельца — повторно, перед выдачей доступа. */
record BreakGlassRequest(
    @NotBlank
        @Pattern(regexp = "PATIENT_COMPLAINT|LEGAL_REQUEST|DOCTOR_UNAVAILABLE|QUALITY_REVIEW|OTHER")
        String reasonCode,
    @NotBlank @Size(min = 10, max = 1000) String reasonText,
    @NotBlank @Size(max = 200) String password) {}
