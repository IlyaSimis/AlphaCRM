package ru.quietclinic.bot;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Клиника, от имени которой работает бот: {@code clinic.bot.clinic-id}, а если он не задан и
 * клиника в базе одна — она. При нескольких клиниках id нужно задать явно.
 */
@Component
class BotClinicResolver {
  private final JdbcTemplate jdbc;
  private final String configured;
  private volatile UUID resolved;

  BotClinicResolver(JdbcTemplate jdbc, @Value("${clinic.bot.clinic-id:}") String configured) {
    this.jdbc = jdbc;
    this.configured = configured == null ? "" : configured.trim();
  }

  /** null — клиника не определена (нет клиник или их несколько без явного id). */
  UUID clinicId() {
    var cached = resolved;
    if (cached != null) return cached;
    var ids =
        configured.isEmpty()
            ? jdbc.queryForList("select id from clinic", UUID.class)
            : jdbc.queryForList(
                "select id from clinic where id = ?", UUID.class, UUID.fromString(configured));
    if (ids.size() != 1) return null;
    resolved = ids.get(0);
    return resolved;
  }
}
