package ru.quietclinic.platform;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Конверт доменного события (docs/target-architecture.md, раздел E): eventId — ключ идемпотентности
 * потребителя, clinicId — в каждом событии. В payload — только идентификаторы и факты: ни заметок,
 * ни диагнозов, ни документов. Потребитель игнорирует неизвестные поля.
 */
public record DomainEvent(
    UUID eventId,
    String type,
    int version,
    UUID clinicId,
    Instant occurredAt,
    UUID actorId,
    String correlationId,
    Map<String, Object> payload) {

  public String text(String field) {
    Object value = payload.get(field);
    return value == null ? null : value.toString();
  }

  public UUID uuid(String field) {
    String value = text(field);
    return value == null || value.isBlank() ? null : UUID.fromString(value);
  }

  public Long number(String field) {
    Object value = payload.get(field);
    if (value == null) return null;
    if (value instanceof Number n) return n.longValue();
    return Long.parseLong(value.toString());
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> list(String field) {
    Object value = payload.get(field);
    return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }
}
