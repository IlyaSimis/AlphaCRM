package ru.quietclinic.platform;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Публикация доменного события: строка в {@code domain_event_outbox} в той же транзакции, что и
 * изменение. После фиксации транзакции релей сразу раздаёт событие потребителям; если что-то пошло
 * не так, событие подберёт фоновый проход ({@link DomainEventRelay#runOnce}).
 */
@Service
public class DomainEvents {
  /** Через сколько фоновый проход подберёт событие, которое не раздалось сразу после фиксации. */
  static final Duration FIRST_RETRY = Duration.ofSeconds(30);

  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final DomainEventRelay relay;

  DomainEvents(JdbcTemplate jdbc, JsonMapper json, @Lazy DomainEventRelay relay) {
    this.jdbc = jdbc;
    this.json = json;
    this.relay = relay;
  }

  /** Событие от имени текущего сотрудника и его клиники. */
  public UUID publish(String type, Map<String, ?> payload) {
    return publish(type, PlatformConfig.clinicId(), PlatformConfig.actorId(), payload);
  }

  public UUID publish(String type, UUID clinicId, UUID actorId, Map<String, ?> payload) {
    var id = UUID.randomUUID();
    var now = Instant.now();
    jdbc.update(
        "insert into domain_event_outbox(id, type, version, clinic_id, occurred_at, actor_id,"
            + " correlation_id, payload, status, attempts, next_attempt_at)"
            + " values (?, ?, 1, ?, ?, ?, ?, ?, 'PENDING', 0, ?)",
        id,
        type,
        clinicId,
        Timestamp.from(now),
        actorId,
        MDC.get("correlationId"),
        json.writeValueAsString(payload),
        Timestamp.from(now.plus(FIRST_RETRY)));
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              relay.dispatch(id);
            }
          });
    } else {
      relay.dispatch(id);
    }
    return id;
  }
}
