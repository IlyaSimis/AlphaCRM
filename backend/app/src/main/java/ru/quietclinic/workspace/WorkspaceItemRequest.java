package ru.quietclinic.workspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Полная редактируемая форма записи. Необязательные параметры получают значения по умолчанию. */
public record WorkspaceItemRequest(
    @NotBlank @Size(max = 240) String title,
    @Size(max = 60) String type,
    @Size(max = 30000) String body,
    @Size(max = 80) String status,
    @Size(max = 20) String color,
    Boolean completed,
    Instant dueAt,
    Instant remindAt,
    UUID patientId,
    UUID appointmentId,
    @Size(max = 30) List<@Size(max = 50) String> tags,
    @Size(max = 40) List<@Valid WorkspaceField> fields,
    @Size(max = 150) List<@Valid ChecklistItem> checklist) {}
