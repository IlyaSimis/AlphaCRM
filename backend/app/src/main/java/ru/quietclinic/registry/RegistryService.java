package ru.quietclinic.registry;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

/**
 * Регистратура: задачи сотрудникам и лист ожидания. Модуль связан с пациентами, врачами и записями
 * только внешними ключами — без зависимостей от их кода.
 */
@Service
class RegistryService {
  private final RegistryTaskRepository tasks;
  private final WaitlistRepository waitlist;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  RegistryService(
      RegistryTaskRepository tasks,
      WaitlistRepository waitlist,
      JdbcTemplate jdbc,
      AuditService audit) {
    this.tasks = tasks;
    this.waitlist = waitlist;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  /**
   * Открытые задачи клиники со сроком до {@code dueTo} включительно — с пациентом и без — или все
   * задачи одного пациента (задачи без пациента в карточку не попадают).
   */
  @Transactional(readOnly = true)
  List<TaskView> tasks(LocalDate dueTo, UUID patientId) {
    requirePermission(Permission.REGISTRY_TASK_READ);
    var rows =
        patientId != null
            ? tasks.findByClinicIdAndPatientIdOrderByDueDate(clinicId(), patientId)
            : tasks.findByClinicIdAndStatusAndDueDateLessThanEqualOrderByDueDate(
                clinicId(), "OPEN", dueTo != null ? dueTo : LocalDate.now());
    return rows.stream().map(TaskView::of).toList();
  }

  @Transactional
  TaskView createTask(CreateTaskRequest request) {
    requirePermission(Permission.REGISTRY_TASK_MANAGE);
    var assigneeActive =
        exists(
            "select count(*) from staff_user where id = ? and active = true and clinic_id = ?",
            request.assigneeId());
    if (!assigneeActive) {
      throw conflict("INVALID_ASSIGNEE", "Assignee not found or inactive");
    }
    if (request.patientId() != null
        && !exists(
            "select count(*) from patient where id = ? and clinic_id = ?", request.patientId())) {
      throw notFound("PATIENT_NOT_FOUND", "Patient not found");
    }
    var task =
        tasks.save(
            new RegistryTask(
                clinicId(),
                request.patientId(),
                request.title().trim(),
                request.dueDate(),
                request.assigneeId(),
                actorId()));
    // В аудит — только наличие пациента, не текст задачи: в нём могут быть данные пациента.
    var reason = task.patientId != null ? "with-patient" : "without-patient";
    audit.record(actorId(), "REGISTRY_TASK_CREATED", "TASK", task.id, "ALLOWED", reason);
    return TaskView.of(task);
  }

  /** Закрыть или открыть снова может администратор или сам исполнитель. */
  @Transactional
  TaskView setDone(UUID id, boolean done) {
    requirePermission(Permission.REGISTRY_TASK_READ);
    var task =
        tasks
            .findByIdAndClinicId(id, clinicId())
            .orElseThrow(() -> notFound("TASK_NOT_FOUND", "Task not found"));
    if (!can(Permission.REGISTRY_TASK_MANAGE) && !actorId().equals(task.assigneeId)) {
      throw forbidden(
          "TASK_CLOSE_FORBIDDEN", "Only the assignee or an administrator can close the task");
    }
    task.status = done ? "DONE" : "OPEN";
    task.doneAt = done ? Instant.now() : null;
    audit.record(
        actorId(),
        done ? "REGISTRY_TASK_DONE" : "REGISTRY_TASK_REOPENED",
        "TASK",
        task.id,
        "ALLOWED",
        null);
    return TaskView.of(task);
  }

  /** Активные ожидания; с врачом и днём — только подходящие к освободившемуся слоту. */
  @Transactional(readOnly = true)
  List<WaitlistView> waitlist(UUID practitionerId, LocalDate day) {
    requirePermission(Permission.WAITLIST_READ);
    var own = clinicPatients();
    return waitlist.findByStatusOrderByWishFrom("ACTIVE").stream()
        .filter(e -> own.contains(e.patientId))
        .filter(e -> practitionerId == null || day == null || e.matches(practitionerId, day))
        .map(WaitlistView::of)
        .toList();
  }

  @Transactional
  WaitlistView addToWaitlist(CreateWaitlistRequest request) {
    requirePermission(Permission.WAITLIST_MANAGE);
    if (request.wishTo().isBefore(request.wishFrom())) {
      throw conflict("INVALID_RANGE", "Period end must not be before its start");
    }
    if (!exists("select count(*) from patient where id = ? and clinic_id = ?", request.patientId())
        || !exists(
            "select count(*) from practitioner where id = ? and clinic_id = ?",
            request.practitionerId())
        || !exists(
            "select count(*) from clinic_service where id = ? and clinic_id = ?",
            request.serviceId())) {
      throw conflict("INVALID_REFERENCE", "Patient, practitioner or service not found");
    }
    if (!exists(
        "select count(*) from clinic_service where id = ? and active = true and clinic_id = ?",
        request.serviceId())) {
      throw conflict("SERVICE_INACTIVE", "The service is switched off");
    }
    try {
      var entry =
          waitlist.saveAndFlush(
              new WaitlistEntry(
                  request.patientId(),
                  request.practitionerId(),
                  request.serviceId(),
                  request.wishFrom(),
                  request.wishTo()));
      audit.record(actorId(), "WAITLIST_ADDED", "WAITLIST", entry.id, "ALLOWED", null);
      return WaitlistView.of(entry);
    } catch (DataIntegrityViolationException ex) {
      throw conflict("INVALID_REFERENCE", "Patient, practitioner or service not found");
    }
  }

  @Transactional
  WaitlistView book(UUID id, BookWaitlistRequest request) {
    requirePermission(Permission.WAITLIST_MANAGE);
    var entry = active(id);
    if (!exists(
        "select count(*) from appointment a join practitioner p on p.id = a.practitioner_id"
            + " where a.id = ? and p.clinic_id = ?",
        request.appointmentId())) {
      throw notFound("APPOINTMENT_NOT_FOUND", "Appointment not found");
    }
    entry.status = "BOOKED";
    entry.appointmentId = request.appointmentId();
    audit.record(actorId(), "WAITLIST_BOOKED", "WAITLIST", entry.id, "ALLOWED", null);
    return WaitlistView.of(entry);
  }

  @Transactional
  WaitlistView cancel(UUID id) {
    requirePermission(Permission.WAITLIST_MANAGE);
    var entry = active(id);
    entry.status = "CANCELLED";
    audit.record(actorId(), "WAITLIST_CANCELLED", "WAITLIST", entry.id, "ALLOWED", null);
    return WaitlistView.of(entry);
  }

  private WaitlistEntry active(UUID id) {
    var entry =
        waitlist
            .findById(id)
            .filter(e -> clinicPatients().contains(e.patientId))
            .orElseThrow(() -> notFound("WAITLIST_NOT_FOUND", "Waitlist entry not found"));
    if (!"ACTIVE".equals(entry.status)) {
      throw conflict("WAITLIST_CLOSED", "Waitlist entry is already closed");
    }
    return entry;
  }

  /** Запрос вида «… where id = ? and clinic_id = ?»: второй параметр — клиника сотрудника. */
  private boolean exists(String sql, UUID id) {
    var count = jdbc.queryForObject(sql, Long.class, id, clinicId());
    return count != null && count > 0;
  }

  /** Пациенты своей клиники: задачи и ожидания по чужим не видны. */
  private Set<UUID> clinicPatients() {
    return new HashSet<>(
        jdbc.queryForList("select id from patient where clinic_id = ?", UUID.class, clinicId()));
  }
}
