package ru.quietclinic.audit;

import java.time.Instant;
import java.util.UUID;

/** Событие журнала. actorName — имя сотрудника своей клиники, подставляется при чтении. */
record AuditView(
    UUID id,
    Instant occurredAt,
    UUID actorId,
    String action,
    String objectType,
    UUID objectId,
    String result,
    String reason,
    String correlationId,
    String actorName) {

  AuditView withActorName(String name) {
    return new AuditView(
        id, occurredAt, actorId, action, objectType, objectId, result, reason, correlationId, name);
  }
}
