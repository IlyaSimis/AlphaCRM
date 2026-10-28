package ru.quietclinic.workspace;

import jakarta.validation.constraints.Size;

/** Сохраняемые параметры представления; неизвестные будущие сочетания не требуют миграции БД. */
public record WorkspaceFilter(
    @Size(max = 240) String query,
    @Size(max = 60) String type,
    @Size(max = 80) String status,
    @Size(max = 50) String tag,
    @Size(max = 20) String due,
    @Size(max = 30) String sort,
    @Size(max = 20) String display) {}
