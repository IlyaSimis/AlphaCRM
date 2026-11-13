package ru.quietclinic.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Возврат: ключ идемпотентности (повтор не делает второй возврат) и код причины. */
record RefundRequest(
    @NotBlank @Size(max = 255) String idempotencyKey,
    @NotBlank @Pattern(regexp = "PATIENT_REQUEST|SERVICE_NOT_PROVIDED|ERROR|OTHER")
        String reason) {}
