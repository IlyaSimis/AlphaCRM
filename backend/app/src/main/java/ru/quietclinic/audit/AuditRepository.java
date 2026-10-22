package ru.quietclinic.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditRepository extends JpaRepository<AuditEvent, UUID> {
  List<AuditEvent> findTop100ByOrderByOccurredAtDesc();
}
