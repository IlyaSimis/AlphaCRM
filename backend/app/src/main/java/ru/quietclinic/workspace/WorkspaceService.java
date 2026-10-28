package ru.quietclinic.workspace;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;
import static ru.quietclinic.platform.PlatformConfig.staffId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.DomainEvents;
import ru.quietclinic.platform.Permission;
import tools.jackson.databind.json.JsonMapper;

/** Личные записи и представления. Каждый запрос ограничен и клиникой, и владельцем. */
@Service
class WorkspaceService {
  private static final Set<String> COLORS =
      Set.of("green", "blue", "orange", "red", "purple", "gray");

  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final AuditService audit;
  private final DomainEvents events;

  WorkspaceService(JdbcTemplate jdbc, JsonMapper json, AuditService audit, DomainEvents events) {
    this.jdbc = jdbc;
    this.json = json;
    this.audit = audit;
    this.events = events;
  }

  @Transactional(readOnly = true)
  List<WorkspaceItemView> items(boolean archived) {
    requirePermission(Permission.WORKSPACE_USE);
    return jdbc.query(
        "select * from workspace.item where clinic_id = ? and owner_id = ? and archived_at is "
            + (archived ? "not null" : "null")
            + " order by completed, coalesce(due_at, '9999-12-31 00:00:00+00'), updated_at desc limit 1000",
        (rs, rowNum) -> item(rs),
        clinicId(),
        staffId());
  }

  @Transactional
  WorkspaceItemView create(WorkspaceItemRequest input) {
    requirePermission(Permission.WORKSPACE_USE);
    var request = normalize(input);
    validateReferences(request);
    var id = UUID.randomUUID();
    var now = Instant.now();
    jdbc.update(
        "insert into workspace.item(id, clinic_id, owner_id, type, title, body, status, color,"
            + " completed, due_at, remind_at, patient_id, appointment_id, tags, fields, checklist,"
            + " created_at, updated_at, completed_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?)",
        id,
        clinicId(),
        staffId(),
        request.type(),
        request.title(),
        request.body(),
        request.status(),
        request.color(),
        request.completed(),
        timestamp(request.dueAt()),
        timestamp(request.remindAt()),
        request.patientId(),
        request.appointmentId(),
        json.writeValueAsString(request.tags()),
        json.writeValueAsString(request.fields()),
        json.writeValueAsString(request.checklist()),
        Timestamp.from(now),
        Timestamp.from(now),
        request.completed() ? Timestamp.from(now) : null);
    audit.record(actorId(), "WORKSPACE_ITEM_CREATED", "WORKSPACE_ITEM", id, "ALLOWED", null);
    events.publish("WorkspaceItemCreated", java.util.Map.of("itemId", id));
    return get(id);
  }

  @Transactional
  WorkspaceItemView update(UUID id, WorkspaceItemRequest input) {
    requirePermission(Permission.WORKSPACE_USE);
    var previous = get(id);
    var request = normalize(input);
    validateReferences(request);
    var now = Instant.now();
    var completedAt =
        request.completed() ? previous.completedAt() != null ? previous.completedAt() : now : null;
    jdbc.update(
        "update workspace.item set type = ?, title = ?, body = ?, status = ?, color = ?, completed ="
            + " ?, due_at = ?, remind_at = ?, patient_id = ?, appointment_id = ?, tags = ?, fields ="
            + " ?, checklist = ?, updated_at = ?, completed_at = ? where id = ? and clinic_id = ?"
            + " and owner_id = ?",
        request.type(),
        request.title(),
        request.body(),
        request.status(),
        request.color(),
        request.completed(),
        timestamp(request.dueAt()),
        timestamp(request.remindAt()),
        request.patientId(),
        request.appointmentId(),
        json.writeValueAsString(request.tags()),
        json.writeValueAsString(request.fields()),
        json.writeValueAsString(request.checklist()),
        Timestamp.from(now),
        timestamp(completedAt),
        id,
        clinicId(),
        staffId());
    audit.record(actorId(), "WORKSPACE_ITEM_UPDATED", "WORKSPACE_ITEM", id, "ALLOWED", null);
    events.publish(
        "WorkspaceItemUpdated", java.util.Map.of("itemId", id, "completed", request.completed()));
    return get(id);
  }

  @Transactional
  WorkspaceItemView archive(UUID id, boolean archived) {
    requirePermission(Permission.WORKSPACE_USE);
    get(id);
    jdbc.update(
        "update workspace.item set archived_at = ?, updated_at = ? where id = ? and clinic_id = ?"
            + " and owner_id = ?",
        archived ? Timestamp.from(Instant.now()) : null,
        Timestamp.from(Instant.now()),
        id,
        clinicId(),
        staffId());
    audit.record(
        actorId(),
        archived ? "WORKSPACE_ITEM_ARCHIVED" : "WORKSPACE_ITEM_RESTORED",
        "WORKSPACE_ITEM",
        id,
        "ALLOWED",
        null);
    return get(id);
  }

  @Transactional(readOnly = true)
  List<SavedView> views() {
    requirePermission(Permission.WORKSPACE_USE);
    return jdbc.query(
        "select * from workspace.saved_view where clinic_id = ? and owner_id = ? order by name",
        (rs, rowNum) ->
            new SavedView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                json.readValue(rs.getString("filter_json"), WorkspaceFilter.class),
                rs.getTimestamp("created_at").toInstant()),
        clinicId(),
        staffId());
  }

  @Transactional
  SavedView createView(SavedViewRequest request) {
    requirePermission(Permission.WORKSPACE_USE);
    var id = UUID.randomUUID();
    try {
      jdbc.update(
          "insert into workspace.saved_view(id, clinic_id, owner_id, name, filter_json, created_at)"
              + " values (?, ?, ?, ?, ?, ?)",
          id,
          clinicId(),
          staffId(),
          request.name().trim(),
          json.writeValueAsString(request.filter()),
          Timestamp.from(Instant.now()));
    } catch (DataIntegrityViolationException ex) {
      throw conflict("WORKSPACE_VIEW_EXISTS", "A view with this name already exists");
    }
    return new SavedView(id, request.name().trim(), request.filter(), Instant.now());
  }

  @Transactional
  void deleteView(UUID id) {
    requirePermission(Permission.WORKSPACE_USE);
    var changed =
        jdbc.update(
            "delete from workspace.saved_view where id = ? and clinic_id = ? and owner_id = ?",
            id,
            clinicId(),
            staffId());
    if (changed == 0) throw notFound("WORKSPACE_VIEW_NOT_FOUND", "Workspace view not found");
  }

  private WorkspaceItemView get(UUID id) {
    var rows =
        jdbc.query(
            "select * from workspace.item where id = ? and clinic_id = ? and owner_id = ?",
            (rs, rowNum) -> item(rs),
            id,
            clinicId(),
            staffId());
    if (rows.isEmpty()) throw notFound("WORKSPACE_ITEM_NOT_FOUND", "Workspace item not found");
    return rows.get(0);
  }

  private WorkspaceItemView item(ResultSet rs) throws SQLException {
    return new WorkspaceItemView(
        rs.getObject("id", UUID.class),
        rs.getString("type"),
        rs.getString("title"),
        rs.getString("body"),
        rs.getString("status"),
        rs.getString("color"),
        rs.getBoolean("completed"),
        instant(rs.getTimestamp("due_at")),
        instant(rs.getTimestamp("remind_at")),
        rs.getObject("patient_id", UUID.class),
        rs.getObject("appointment_id", UUID.class),
        List.of(json.readValue(rs.getString("tags"), String[].class)),
        List.of(json.readValue(rs.getString("fields"), WorkspaceField[].class)),
        List.of(json.readValue(rs.getString("checklist"), ChecklistItem[].class)),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        instant(rs.getTimestamp("completed_at")),
        instant(rs.getTimestamp("archived_at")));
  }

  private WorkspaceItemRequest normalize(WorkspaceItemRequest input) {
    var tags =
        input.tags() == null
            ? List.<String>of()
            : input.tags().stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(LinkedHashSet<String>::new, LinkedHashSet::add, LinkedHashSet::addAll)
                .stream()
                .toList();
    var fields =
        input.fields() == null
            ? List.<WorkspaceField>of()
            : input.fields().stream()
                .map(
                    field ->
                        new WorkspaceField(
                            field.name().trim(), field.value() == null ? "" : field.value()))
                .toList();
    var checklist =
        input.checklist() == null
            ? List.<ChecklistItem>of()
            : input.checklist().stream()
                .map(item -> new ChecklistItem(item.text().trim(), item.done()))
                .toList();
    var color = input.color() == null ? "green" : input.color().trim().toLowerCase();
    return new WorkspaceItemRequest(
        input.title().trim(),
        text(input.type(), "Задача"),
        input.body() == null ? "" : input.body(),
        text(input.status(), "Без статуса"),
        COLORS.contains(color) ? color : "green",
        Boolean.TRUE.equals(input.completed()),
        input.dueAt(),
        input.remindAt(),
        input.patientId(),
        input.appointmentId(),
        tags,
        fields,
        checklist);
  }

  private void validateReferences(WorkspaceItemRequest request) {
    if (request.patientId() != null) {
      requirePermission(Permission.PATIENT_READ, Permission.PATIENT_READ_CARE);
      if (!exists(
          "select count(*) from patient where id = ? and clinic_id = ?", request.patientId())) {
        throw notFound("PATIENT_NOT_FOUND", "Patient not found");
      }
      if (!can(Permission.PATIENT_READ)
          && !exists(
              "select count(*) from care_relationship where patient_id = ? and practitioner_id = ?"
                  + " and valid_until > current_timestamp",
              request.patientId(),
              actorId())) {
        throw notFound("PATIENT_NOT_FOUND", "Patient not found");
      }
    }
    if (request.appointmentId() != null) {
      requirePermission(Permission.SCHEDULE_READ);
      if (!exists(
          "select count(*) from appointment a join practitioner p on p.id = a.practitioner_id"
              + " where a.id = ? and p.clinic_id = ?",
          request.appointmentId())) {
        throw notFound("APPOINTMENT_NOT_FOUND", "Appointment not found");
      }
      if (request.patientId() != null
          && !exists(
              "select count(*) from appointment where id = ? and patient_id = ?",
              request.appointmentId(),
              request.patientId())) {
        throw conflict("WORKSPACE_REFERENCE_MISMATCH", "Appointment belongs to another patient");
      }
    }
  }

  /** Запрос с id и клиникой; второй вариант — с id и дополнительным атрибутом владельца. */
  private boolean exists(String sql, UUID first) {
    var count = jdbc.queryForObject(sql, Long.class, first, clinicId());
    return count != null && count > 0;
  }

  private boolean exists(String sql, UUID first, UUID second) {
    var count = jdbc.queryForObject(sql, Long.class, first, second);
    return count != null && count > 0;
  }

  private static String text(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
