package ru.quietclinic.registry;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.ClinicTime;

/**
 * Пациент попросил в Telegram-боте живого оператора: регистратура получает задачу на сегодня. Текст
 * задачи присылает бот. Исполнитель — сотрудник с логином {@code clinic.bot.task-assignee}, если он
 * активен, иначе первый по ФИО активный администратор клиники.
 */
@Service
public class OperatorRequests {
  static final String ROLE = "BOT";
  static final int MAX_TITLE = 200;

  private final RegistryTaskRepository tasks;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final ClinicTime time;
  private final String assigneeUsername;

  OperatorRequests(
      RegistryTaskRepository tasks,
      JdbcTemplate jdbc,
      AuditService audit,
      ClinicTime time,
      @Value("${clinic.bot.task-assignee:}") String assigneeUsername) {
    this.tasks = tasks;
    this.jdbc = jdbc;
    this.audit = audit;
    this.time = time;
    this.assigneeUsername = assigneeUsername == null ? "" : assigneeUsername.trim();
  }

  /** Новая задача регистратуры со сроком «сегодня»; пациент — если бот его знает. */
  @Transactional
  public UUID create(String title, UUID patientId) {
    requireRole(ROLE);
    String text = title.trim();
    if (text.length() > MAX_TITLE) text = text.substring(0, MAX_TITLE);
    RegistryTask task =
        tasks.save(
            new RegistryTask(clinicId(), patientId, text, time.today(), assignee(), actorId()));
    String reason = task.patientId != null ? "with-patient" : "without-patient";
    audit.record(actorId(), "REGISTRY_TASK_CREATED", "TASK", task.id, "ALLOWED", reason);
    return task.id;
  }

  private UUID assignee() {
    if (!assigneeUsername.isEmpty()) {
      List<UUID> named =
          jdbc.queryForList(
              "select id from staff_user where username = ? and active = true and clinic_id = ?",
              UUID.class,
              assigneeUsername,
              clinicId());
      if (!named.isEmpty()) return named.get(0);
    }
    List<UUID> admins =
        jdbc.queryForList(
            "select u.id from staff_user u where u.active = true and u.clinic_id = ?"
                + " and exists (select 1 from staff_role r where r.staff_id = u.id"
                + " and r.role in ('OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR'))"
                + " order by u.full_name, u.id",
            UUID.class,
            clinicId());
    if (admins.isEmpty()) {
      throw conflict("NO_TASK_ASSIGNEE", "No active administrator to assign the request to");
    }
    return admins.get(0);
  }
}
