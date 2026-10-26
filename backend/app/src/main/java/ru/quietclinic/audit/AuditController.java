package ru.quietclinic.audit;

import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    requireRole("MANAGER", "ADMINISTRATOR");
    var actors = clinicActors();
    return audit.recent(actors.keySet()).stream()
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
    requireRole("MANAGER", "ADMINISTRATOR");
    var actors = clinicActors();
    var safePage = Math.max(page, 0);
    var safeSize = Math.min(Math.max(size, 1), 100);
    var rows = audit.page(actors.keySet(), result, safePage, safeSize);
    return rows.withItems(
        rows.items().stream().map(e -> e.withActorName(actors.get(e.actorId()))).toList());
  }

  /** Авторы действий своей клиники и их имена: у врача автор — id карточки врача. */
  private Map<UUID, String> clinicActors() {
    var actors = new HashMap<UUID, String>();
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
