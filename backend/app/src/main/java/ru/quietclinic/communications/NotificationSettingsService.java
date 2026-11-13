package ru.quietclinic.communications;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

/** Настройки уведомлений общие для клиники, а не для отдельного мессенджера. */
@Service
class NotificationSettingsService {
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  NotificationSettingsService(JdbcTemplate jdbc, AuditService audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  NotificationSettings get() {
    requirePermission(Permission.INTEGRATION_READ);
    return forClinic(clinicId());
  }

  @Transactional
  NotificationSettings update(NotificationSettings settings) {
    requirePermission(Permission.INTEGRATION_MANAGE);
    UUID clinic = clinicId();
    int updated =
        jdbc.update(
            "update clinic_notification_settings set reminder_24h_enabled = ?,"
                + " reminder_3h_enabled = ?, updated_at = ? where clinic_id = ?",
            settings.reminder24hEnabled(),
            settings.reminder3hEnabled(),
            Timestamp.from(Instant.now()),
            clinic);
    if (updated == 0) {
      jdbc.update(
          "insert into clinic_notification_settings(clinic_id, reminder_24h_enabled,"
              + " reminder_3h_enabled, updated_at) values (?, ?, ?, ?)",
          clinic,
          settings.reminder24hEnabled(),
          settings.reminder3hEnabled(),
          Timestamp.from(Instant.now()));
    }
    audit.record(
        actorId(),
        "NOTIFICATION_SETTINGS_UPDATED",
        "CLINIC",
        clinic,
        "ALLOWED",
        "24h=" + settings.reminder24hEnabled() + ",3h=" + settings.reminder3hEnabled());
    return settings;
  }

  /** Внутреннее чтение планировщиком: до первого сохранения действует напоминание за сутки. */
  NotificationSettings forClinic(UUID clinic) {
    return jdbc
        .query(
            "select reminder_24h_enabled, reminder_3h_enabled"
                + " from clinic_notification_settings where clinic_id = ?",
            (row, index) ->
                new NotificationSettings(row.getBoolean(1), row.getBoolean(2)),
            clinic)
        .stream()
        .findFirst()
        .orElse(NotificationSettings.DEFAULT);
  }
}
