package ru.quietclinic.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Пункт чек-листа без ограничения его смысла приложением. */
public record ChecklistItem(@NotBlank @Size(max = 500) String text, boolean done) {}
