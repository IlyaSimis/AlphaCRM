package ru.quietclinic.registry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/** Задача регистратуры; {@code patientId} может отсутствовать. */
record CreateTaskRequest(
    @NotBlank @Size(max = 200) String title,
    @NotNull LocalDate dueDate,
    @NotNull UUID assigneeId,
    UUID patientId) {}
