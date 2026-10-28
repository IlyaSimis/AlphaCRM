package ru.quietclinic.registry;

import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.DomainEvent;
import ru.quietclinic.platform.DomainEventHandler;

/**
 * Потребитель {@code StockShortage} (сценарий D7): нехватка на складе не блокирует приём, а
 * становится задачей регистратуры на сегодня. Исполнитель — первый активный владелец или
 * администратор клиники; пациента у задачи нет.
 */
@Component
class StockShortageTasks implements DomainEventHandler {
  private final RegistryTaskRepository tasks;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final ClinicTime time;
  private final String titleTemplate;

  StockShortageTasks(
      RegistryTaskRepository tasks,
      JdbcTemplate jdbc,
      AuditService audit,
      ClinicTime time,
      @Value("${clinic.registry.stock-shortage-title:Stock shortage: %s (missing %s)}")
          String titleTemplate) {
    this.tasks = tasks;
    this.jdbc = jdbc;
    this.audit = audit;
    this.time = time;
    this.titleTemplate = titleTemplate;
  }

  @Override
  public String consumer() {
    return "registry.stock-shortage-task";
  }

  @Override
  public Set<String> types() {
    return Set.of("StockShortage");
  }

  @Override
  public void handle(DomainEvent event) {
    var assignee =
        jdbc
            .queryForList(
                "select id from staff_user where clinic_id = ? and active = true"
                    + " and role in ('OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR')"
                    + " order by created_at",
                UUID.class,
                event.clinicId())
            .stream()
            .findFirst();
    if (assignee.isEmpty()) return;
    var title = titleTemplate.formatted(event.text("itemName"), event.text("missing"));
    var task =
        tasks.save(
            new RegistryTask(
                event.clinicId(),
                null,
                title.length() > 200 ? title.substring(0, 200) : title,
                time.today(),
                assignee.get(),
                event.actorId()));
    audit.record(
        event.actorId(), "REGISTRY_TASK_CREATED", "TASK", task.id, "ALLOWED", "stock-shortage");
  }
}
