package ru.quietclinic.audit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditRepository extends JpaRepository<AuditEvent, UUID> {
  /** Последние события сотрудников одной клиники. */
  List<AuditEvent> findTop100ByActorIdInOrderByOccurredAtDesc(Collection<UUID> actorIds);
}
