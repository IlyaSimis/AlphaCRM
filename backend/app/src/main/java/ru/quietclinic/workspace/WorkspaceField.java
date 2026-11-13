package ru.quietclinic.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Произвольное поле записи: название и значение задаёт сотрудник. */
public record WorkspaceField(
    @NotBlank @Size(max = 80) String name, @Size(max = 2000) String value) {}
