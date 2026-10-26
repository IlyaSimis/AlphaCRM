package ru.quietclinic.audit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditRepository extends JpaRepository<AuditEvent, UUID> {
  /** Последние события сотрудников одной клиники. */
  List<AuditEvent> findTop100ByActorIdInOrderByOccurredAtDesc(Collection<UUID> actorIds);

  /** Страница событий сотрудников одной клиники, новые сверху. */
  Page<AuditEvent> findByActorIdInOrderByOccurredAtDescIdDesc(
      Collection<UUID> actorIds, Pageable page);

  Page<AuditEvent> findByActorIdInAndResultOrderByOccurredAtDescIdDesc(
      Collection<UUID> actorIds, String result, Pageable page);
}
