package ru.quietclinic.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Запись рабочего пространства. Автор и исполнитель видят одну задачу; у личной записи они совпадают.
 */
public record WorkspaceItemView(
    UUID id,
    String type,
    String title,
    String body,
    String status,
    String color,
    boolean completed,
    Instant dueAt,
    Instant remindAt,
    UUID patientId,
    UUID appointmentId,
    List<String> tags,
    List<WorkspaceField> fields,
    List<ChecklistItem> checklist,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt,
    Instant archivedAt,
    UUID creatorId,
    UUID assigneeId) {}
