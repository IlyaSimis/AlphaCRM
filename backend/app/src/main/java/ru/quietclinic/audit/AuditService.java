package ru.quietclinic.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

interface AuditRepository extends JpaRepository<AuditEvent, UUID> {
  List<AuditEvent> findTop100ByOrderByOccurredAtDesc();
}

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

@Service
public class AuditService {
  private final AuditRepository repository;

  AuditService(AuditRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      UUID actorId, String action, String objectType, UUID objectId, String result, String reason) {
    repository.save(new AuditEvent(actorId, action, objectType, objectId, result, reason));
  }

  @Transactional(readOnly = true)
  public List<AuditView> recent() {
    return repository.findTop100ByOrderByOccurredAtDesc().stream()
        .map(
            e ->
                new AuditView(
                    e.id,
                    e.occurredAt,
                    e.actorId,
                    e.action,
                    e.objectType,
                    e.objectId,
                    e.result,
                    e.reason,
                    e.correlationId))
        .toList();
  }
}
