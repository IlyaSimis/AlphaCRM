package ru.quietclinic.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;

@Entity
@Table(name = "audit_event")
class AuditEvent {
  @Id UUID id;

  @Column(nullable = false)
  Instant occurredAt;

  @Column(nullable = false)
  UUID actorId;

  @Column(nullable = false)
  String action;

  @Column(nullable = false)
  String objectType;

  @Column(nullable = false)
  UUID objectId;

  @Column(nullable = false)
  String result;

  String reason;
  String correlationId;

  protected AuditEvent() {}

  AuditEvent(
      UUID actorId, String action, String objectType, UUID objectId, String result, String reason) {
    this.id = UUID.randomUUID();
    this.occurredAt = Instant.now();
    this.actorId = actorId;
    this.action = action;
    this.objectType = objectType;
    this.objectId = objectId;
    this.result = result;
    this.reason = reason;
    this.correlationId = MDC.get("correlationId");
  }
}
