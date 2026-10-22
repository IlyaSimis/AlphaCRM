package ru.quietclinic.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
