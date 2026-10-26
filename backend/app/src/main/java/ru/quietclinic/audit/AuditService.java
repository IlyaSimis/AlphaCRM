package ru.quietclinic.audit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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

  /** Последние 100 событий, которые совершили перечисленные сотрудники (своей клиники). */
  @Transactional(readOnly = true)
  public List<AuditView> recent(Collection<UUID> actorIds) {
    if (actorIds.isEmpty()) return List.of();
    return repository.findTop100ByActorIdInOrderByOccurredAtDesc(actorIds).stream()
        .map(AuditService::view)
        .toList();
  }

  /**
   * Страница журнала: page с нуля, size событий, новые сверху. result — только ALLOWED или DENIED;
   * пусто — все.
   */
  @Transactional(readOnly = true)
  public AuditPage page(Collection<UUID> actorIds, String result, int page, int size) {
    if (actorIds.isEmpty()) return new AuditPage(List.of(), page, size, 0);
    var request = PageRequest.of(page, size);
    var rows =
        result == null || result.isBlank()
            ? repository.findByActorIdInOrderByOccurredAtDescIdDesc(actorIds, request)
            : repository.findByActorIdInAndResultOrderByOccurredAtDescIdDesc(
                actorIds, result, request);
    return new AuditPage(
        rows.getContent().stream().map(AuditService::view).toList(),
        page,
        size,
        rows.getTotalElements());
  }

  private static AuditView view(AuditEvent e) {
    return new AuditView(
        e.id,
        e.occurredAt,
        e.actorId,
        e.action,
        e.objectType,
        e.objectId,
        e.result,
        e.reason,
        e.correlationId,
        null);
  }
}
