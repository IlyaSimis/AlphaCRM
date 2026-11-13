package ru.quietclinic.billing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Нейтральный контракт уведомления; адаптер банка преобразует свой ответ в эти поля. */
record TerminalEventRequest(
    @NotBlank @Size(max = 128) String eventId,
    @NotBlank @Size(max = 255) String paymentRef,
    @NotBlank @Pattern(regexp = "PENDING|SUCCEEDED|CANCELLED|FAILED|TIMEOUT") String status,
    @Size(max = 255) String failureReason) {}
