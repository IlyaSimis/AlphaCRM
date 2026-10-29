package ru.quietclinic.telephony;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Клиника одного подключения Mango; при нескольких клиниках id обязателен в настройках. */
@Component
class MangoClinicResolver {
  private final JdbcTemplate jdbc;
  private final String configured;
  private volatile UUID resolved;

  MangoClinicResolver(
      JdbcTemplate jdbc, @Value("${clinic.telephony.mango.clinic-id:}") String configured) {
    this.jdbc = jdbc;
    this.configured = configured == null ? "" : configured.trim();
  }

  UUID clinicId() {
    var cached = resolved;
    if (cached != null) return cached;
    try {
      var ids =
          configured.isEmpty()
              ? jdbc.queryForList("select id from clinic", UUID.class)
              : jdbc.queryForList(
                  "select id from clinic where id = ?", UUID.class, UUID.fromString(configured));
      if (ids.size() != 1) return null;
      resolved = ids.get(0);
      return resolved;
    } catch (IllegalArgumentException error) {
      return null;
    }
  }
}
