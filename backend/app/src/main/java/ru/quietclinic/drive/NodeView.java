package ru.quietclinic.drive;

import java.time.Instant;
import java.util.UUID;

/** Узел диска и право текущего сотрудника на него (VIEW, EDIT, MANAGE). */
record NodeView(
    UUID id,
    UUID parentId,
    String kind,
    String name,
    UUID ownerId,
    String ownerName,
    long sizeBytes,
    int currentVersion,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    String permission,
    boolean shared) {}
