package ru.quietclinic.audit;

import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.platform.Permission;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
  private final AuditService audit;
  private final JdbcTemplate jdbc;

  AuditController(AuditService audit, JdbcTemplate jdbc) {
    this.audit = audit;
    this.jdbc = jdbc;
  }

  /**
   * Последние события сотрудников своей клиники. Клинический текст в журнал не попадает по
   * построению.
   */
  @GetMapping
  List<AuditView> recent(
      @RequestParam(required = false) String result,
      @RequestParam(required = false) String objectType) {
    requirePermission(Permission.AUDIT_READ, Permission.AUDIT_READ_PLATFORM);
    Map<UUID, String> actors = clinicActors();
    return audit.recent(actors.keySet()).stream()
        .filter(AuditController::visible)
        .filter(e -> result == null || result.isBlank() || e.result().equals(result))
        .filter(
            e -> objectType == null || objectType.isBlank() || e.objectType().equals(objectType))
        .map(e -> e.withActorName(actors.get(e.actorId())))
        .toList();
  }

  /** Журнал постранично: по 20 событий (size 1–100), новые сверху; total — для числа страниц. */
  @GetMapping("/page")
  AuditPage page(
      @RequestParam(required = false) String result,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    requirePermission(Permission.AUDIT_READ, Permission.AUDIT_READ_PLATFORM);
    Map<UUID, String> actors = clinicActors();
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    AuditPage rows =
        can(Permission.AUDIT_READ)
            ? audit.page(actors.keySet(), result, safePage, safeSize)
            : audit.page(actors.keySet(), result, CLINICAL_OBJECTS, safePage, safeSize);
    return rows.withItems(
        rows.items().stream().map(e -> e.withActorName(actors.get(e.actorId()))).toList());
  }

  /**
   * Объекты клинического контура: техподдержка (audit.event.read-platform) их в журнале не видит —
   * ни действий над пациентами, ни над приёмами и документами.
   */
  static final Set<String> CLINICAL_OBJECTS =
      Set.of("PATIENT", "CONSENT", "ENCOUNTER", "DOCUMENT", "PATIENT_FILE", "CLINIC");

  private static boolean visible(AuditView event) {
    return can(Permission.AUDIT_READ) || !CLINICAL_OBJECTS.contains(event.objectType());
  }

  /** Авторы действий своей клиники и их имена: у врача автор — id карточки врача. */
  private Map<UUID, String> clinicActors() {
    Map<UUID, String> actors = new HashMap<>();
    jdbc.query(
        "select id, full_name from practitioner where clinic_id = ?",
        (ResultSet row) -> {
          actors.put(row.getObject("id", UUID.class), row.getString("full_name"));
        },
        clinicId());
    jdbc.query(
        "select coalesce(practitioner_id, id) as actor_id, full_name from staff_user"
            + " where clinic_id = ?",
        (ResultSet row) -> {
          actors.put(row.getObject("actor_id", UUID.class), row.getString("full_name"));
        },
        clinicId());
    // Доставщик outbox пишет события от имени клиники: автор — сама клиника.
    jdbc.query(
        "select id, name from clinic where id = ?",
        (ResultSet row) -> {
          actors.put(row.getObject("id", UUID.class), row.getString("name"));
        },
        clinicId());
    return actors;
  }
}
