package ru.quietclinic.inventory;

import java.time.Instant;
import java.util.UUID;

/** Операция маркировки в очереди: без списка марок и без связи с приёмом. */
record MarkingOperationView(
    UUID id,
    String type,
    String status,
    int codes,
    int attempts,
    String operatorRef,
    String lastError,
    UUID documentId,
    Instant createdAt,
    Instant updatedAt) {
  static MarkingOperationView of(MarkingOperation op) {
    return new MarkingOperationView(
        op.id,
        op.type,
        op.status,
        op.sgtins.size(),
        op.attempts,
        op.operatorRef,
        op.lastError,
        op.documentId,
        op.createdAt,
        op.updatedAt);
  }
}
