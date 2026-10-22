package ru.quietclinic.audit;

import java.time.Instant;
import java.util.UUID;

record AuditView(
    UUID id,
    Instant occurredAt,
    UUID actorId,
    String action,
    String objectType,
    UUID objectId,
    String result,
    String reason,
    String correlationId) {}
