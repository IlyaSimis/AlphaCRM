package ru.quietclinic.communications;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {
  boolean existsByIdempotencyKey(String idempotencyKey);

  Optional<OutboxMessage> findByIdempotencyKey(String idempotencyKey);

  /** Очередные попытки: PENDING и FAILED, чьё время пришло, по порядку постановки. */
  List<OutboxMessage> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAt(
      Collection<String> statuses, Instant now);

  /** Попытки, которые начались и не закончились (процесс остановили посреди отправки). */
  List<OutboxMessage> findByStatusAndLastAttemptAtBefore(String status, Instant before);

  List<OutboxMessage> findByAggregateIdAndTemplateInAndStatusIn(
      UUID aggregateId, Collection<String> templates, Collection<String> statuses);

  List<OutboxMessage> findTop100ByClinicIdOrderByCreatedAtDesc(UUID clinicId);

  List<OutboxMessage> findTop100ByClinicIdAndStatusOrderByCreatedAtDesc(
      UUID clinicId, String status);

  List<OutboxMessage> findByAggregateIdOrderByCreatedAt(UUID aggregateId);
}
