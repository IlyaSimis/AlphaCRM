package ru.quietclinic.platform;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Релей событий внутри монолита: outbox → потребители ({@link DomainEventHandler}) → inbox. Каждый
 * потребитель обрабатывает событие в отдельной транзакции вместе с отметкой в inbox; ошибка одного
 * потребителя не мешает другим, событие остаётся PENDING и повторяется с паузой. На следующем этапе
 * миграции этот же outbox будет читать релей в RabbitMQ.
 */
@Component
public class DomainEventRelay {
  static final int MAX_ATTEMPTS = 10;
  private static final Logger log = LoggerFactory.getLogger(DomainEventRelay.class);

  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final List<DomainEventHandler> handlers;
  private final TransactionTemplate newTransaction;

  DomainEventRelay(
      JdbcTemplate jdbc,
      JsonMapper json,
      List<DomainEventHandler> handlers,
      PlatformTransactionManager transactions) {
    this.jdbc = jdbc;
    this.json = json;
    this.handlers = handlers;
    this.newTransaction = new TransactionTemplate(transactions);
    newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Все события, срок повтора которых наступил. Возвращает число обработанных событий. */
  public int runOnce(Instant now) {
    var due =
        jdbc.queryForList(
            "select id from domain_event_outbox where status = 'PENDING' and next_attempt_at <= ?"
                + " order by occurred_at limit 100",
            UUID.class,
            Timestamp.from(now));
    due.forEach(this::dispatch);
    return due.size();
  }

  /** Раздать одно событие всем его потребителям. */
  public void dispatch(UUID eventId) {
    var rows = jdbc.query("select * from domain_event_outbox where id = ?", this::event, eventId);
    if (rows.isEmpty()) return;
    var event = rows.get(0);
    String error = null;
    for (var handler : handlers) {
      if (!handler.types().contains(event.type())) continue;
      try {
        newTransaction.executeWithoutResult(status -> deliver(handler, event));
      } catch (RuntimeException ex) {
        error = handler.consumer() + ":" + code(ex);
        log.warn(
            "Event {} {} failed in {}: {}", event.type(), eventId, handler.consumer(), code(ex));
      }
    }
    if (error == null) {
      jdbc.update(
          "update domain_event_outbox set status = 'PUBLISHED', published_at = ?, last_error = null"
              + " where id = ? and status <> 'PUBLISHED'",
          Timestamp.from(Instant.now()),
          eventId);
    } else {
      var attempts =
          jdbc.queryForObject(
                  "select attempts from domain_event_outbox where id = ?", Integer.class, eventId)
              + 1;
      var pause = Duration.ofSeconds(Math.min(3600, 30L * (1L << Math.min(attempts, 7))));
      jdbc.update(
          "update domain_event_outbox set attempts = ?, last_error = ?, next_attempt_at = ?,"
              + " status = ? where id = ?",
          attempts,
          error.length() > 120 ? error.substring(0, 120) : error,
          Timestamp.from(Instant.now().plus(pause)),
          attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING",
          eventId);
    }
  }

  private void deliver(DomainEventHandler handler, DomainEvent event) {
    try {
      jdbc.update(
          "insert into domain_event_inbox(consumer, event_id, processed_at) values (?, ?, ?)",
          handler.consumer(),
          event.eventId(),
          Timestamp.from(Instant.now()));
    } catch (DuplicateKeyException ex) {
      return; // уже обработано: повтор безопасен
    }
    var previous = SecurityContextHolder.getContext();
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new SystemPrincipal(event.clinicId(), event.actorId()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + SystemPrincipal.ROLE))));
    SecurityContextHolder.setContext(context);
    try {
      handler.handle(event);
    } finally {
      SecurityContextHolder.setContext(previous);
    }
  }

  private static String code(RuntimeException ex) {
    return ex instanceof DomainException domain ? domain.code : ex.getClass().getSimpleName();
  }

  private DomainEvent event(ResultSet rs, int row) throws SQLException {
    Map<String, Object> payload = json.readValue(rs.getString("payload"), new TypeReference<>() {});
    return new DomainEvent(
        rs.getObject("id", UUID.class),
        rs.getString("type"),
        rs.getInt("version"),
        rs.getObject("clinic_id", UUID.class),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getObject("actor_id", UUID.class),
        rs.getString("correlation_id"),
        payload);
  }
}
