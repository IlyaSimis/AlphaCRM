package ru.quietclinic.telephony;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.integrations.MangoClient;
import ru.quietclinic.patients.PatientContactGateway;
import ru.quietclinic.platform.Permission;
import ru.quietclinic.telephony.TelephonyController.CallView;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Журнал звонков клиники. Записи разговоров и клинический текст здесь не хранятся. */
@Service
class TelephonyService {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final MangoRoutingGateway mango;
  private final AuditService audit;
  private final PatientContactGateway patientContacts;

  TelephonyService(
      JdbcTemplate jdbc,
      JsonMapper json,
      MangoRoutingGateway mango,
      AuditService audit,
      PatientContactGateway patientContacts) {
    this.jdbc = jdbc;
    this.json = json;
    this.mango = mango;
    this.audit = audit;
    this.patientContacts = patientContacts;
  }

  CallView start(UUID patientId, String requestedExtension) {
    requirePermission(Permission.PATIENT_CONTACTS_READ);
    UUID clinic = clinicId();
    MangoClient client = mango.forClinic(clinic);
    if (!client.configured())
      throw conflict("TELEPHONY_NOT_CONFIGURED", "Mango integration is not configured");
    String phone =
        normalize(
            patientContacts
                .value(patientId, clinic, "PHONE")
                .orElseThrow(() -> notFound("PATIENT_NOT_FOUND", "Patient not found")));
    if (phone.isEmpty()) throw conflict("PATIENT_PHONE_MISSING", "Patient has no callable phone");
    String extension =
        requestedExtension == null || requestedExtension.isBlank()
            ? client.defaultExtension()
            : requestedExtension.trim();
    if (!extension.matches("[0-9]{1,32}")) {
      throw conflict("MANGO_EXTENSION_REQUIRED", "Enter the employee extension");
    }
    UUID id = UUID.randomUUID();
    String commandId = "alphacrm-" + id;
    Instant now = Instant.now();
    jdbc.update(
        "insert into telephony_call(id, clinic_id, patient_id, call_id, command_id, direction,"
            + " phone, extension, status, occurred_at, updated_at, created_by)"
            + " values(?,?,?,?,?,'OUTBOUND',?,?,'REQUESTING',?,?,?)",
        id,
        clinic,
        patientId,
        commandId,
        commandId,
        phone,
        extension,
        Timestamp.from(now),
        Timestamp.from(now),
        actorId());
    try {
      client.callback(commandId, extension, phone);
    } catch (RuntimeException error) {
      jdbc.update(
          "update telephony_call set status = 'FAILED', updated_at = ? where id = ?",
          Timestamp.from(Instant.now()),
          id);
      throw error;
    }
    jdbc.update(
        "update telephony_call set status = 'REQUESTED', updated_at = ? where id = ?",
        Timestamp.from(Instant.now()),
        id);
    audit.record(actorId(), "TELEPHONY_CALL_REQUESTED", "TELEPHONY_CALL", id, "ALLOWED", "mango");
    return new CallView(id, "OUTBOUND", "REQUESTED", extension, now, false);
  }

  @Transactional(readOnly = true)
  List<CallView> history(UUID patientId) {
    requirePermission(Permission.PATIENT_CONTACTS_READ);
    Integer exists =
        jdbc.queryForObject(
            "select count(*) from patient where id = ? and clinic_id = ?",
            Integer.class,
            patientId,
            clinicId());
    if (exists == null || exists == 0) throw notFound("PATIENT_NOT_FOUND", "Patient not found");
    return jdbc.query(
        "select id, direction, status, extension, occurred_at, recording_id"
            + " from telephony_call where clinic_id = ? and patient_id = ?"
            + " order by occurred_at desc limit 50",
        (rs, row) ->
            new CallView(
                rs.getObject("id", UUID.class),
                rs.getString("direction"),
                rs.getString("status"),
                rs.getString("extension"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("recording_id") != null),
        clinicId(),
        patientId);
  }

  /** Идемпотентное уведомление: повтор entry/call/seq не меняет журнал второй раз. */
  @Transactional
  void callEvent(String body) {
    try {
      JsonNode root = json.readValue(body, JsonNode.class);
      String entryId = required(root, "entry_id");
      String callId = text(root, "call_id").isEmpty() ? entryId : text(root, "call_id");
      int sequence = root.path("seq").asInt(0);
      if (!registerEvent(entryId, callId, sequence, "CALL")) return;
      Instant occurred = eventTime(root);
      String commandId = text(root, "command_id");
      JsonNode from = root.path("from");
      JsonNode to = root.path("to");
      String fromExtension = text(from, "extension");
      String toExtension = text(to, "extension");
      String direction = fromExtension.isEmpty() ? "INBOUND" : "OUTBOUND";
      String phone = "INBOUND".equals(direction) ? text(from, "number") : text(to, "number");
      String extension = "INBOUND".equals(direction) ? toExtension : fromExtension;
      CallRow existing = findExisting(callId, commandId);
      UUID patientId = existing == null ? uniquePatient(phone) : existing.patientId;
      String status = text(root, "call_state");
      if (status.isEmpty()) status = "UNKNOWN";
      if (existing == null) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "insert into telephony_call(id, clinic_id, patient_id, entry_id, call_id, command_id,"
                + " direction, phone, extension, status, sequence_number, occurred_at, updated_at)"
                + " values(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id,
            clinicId(),
            patientId,
            entryId,
            callId,
            emptyToNull(commandId),
            direction,
            phone,
            emptyToNull(extension),
            status,
            sequence,
            Timestamp.from(occurred),
            Timestamp.from(Instant.now()));
      } else if (sequence >= existing.sequence) {
        jdbc.update(
            "update telephony_call set patient_id = coalesce(patient_id, ?), entry_id = ?,"
                + " call_id = ?, status = ?, sequence_number = ?, extension = coalesce(nullif(extension, ''), ?),"
                + " updated_at = ? where id = ? and clinic_id = ?",
            patientId,
            entryId,
            callId,
            status,
            sequence,
            emptyToNull(extension),
            Timestamp.from(Instant.now()),
            existing.id,
            clinicId());
      }
    } catch (DuplicateKeyException error) {
      throw error;
    } catch (RuntimeException error) {
      throw badRequest("MANGO_EVENT_INVALID", "Mango event is invalid");
    }
  }

  @Transactional
  void recordingEvent(String body) {
    try {
      JsonNode root = json.readValue(body, JsonNode.class);
      String entryId = required(root, "entry_id");
      String callId = required(root, "call_id");
      int sequence = root.path("seq").asInt(0);
      if (!registerEvent(entryId, callId, sequence, "RECORDING")) return;
      String recordingId = required(root, "recording_id");
      jdbc.update(
          "update telephony_call set recording_id = ?, updated_at = ?"
              + " where clinic_id = ? and call_id = ?",
          recordingId,
          Timestamp.from(Instant.now()),
          clinicId(),
          callId);
    } catch (RuntimeException error) {
      throw badRequest("MANGO_EVENT_INVALID", "Mango recording event is invalid");
    }
  }

  private boolean registerEvent(String entryId, String callId, int sequence, String type) {
    try {
      return jdbc.update(
              "insert into telephony_event(id, clinic_id, entry_id, call_id, sequence_number,"
                  + " event_type, received_at) values(?,?,?,?,?,?,?)",
              UUID.randomUUID(),
              clinicId(),
              entryId,
              callId,
              sequence,
              type,
              Timestamp.from(Instant.now()))
          == 1;
    } catch (DuplicateKeyException error) {
      return false;
    }
  }

  private CallRow findExisting(String callId, String commandId) {
    List<CallRow> rows;
    if (!commandId.isEmpty()) {
      rows =
          jdbc.query(
              "select id, patient_id, sequence_number from telephony_call"
                  + " where clinic_id = ? and command_id = ?",
              this::callRow,
              clinicId(),
              commandId);
      if (!rows.isEmpty()) return rows.get(0);
    }
    rows =
        jdbc.query(
            "select id, patient_id, sequence_number from telephony_call"
                + " where clinic_id = ? and call_id = ?",
            this::callRow,
            clinicId(),
            callId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private CallRow callRow(ResultSet rs, int row) throws SQLException {
    return new CallRow(
        rs.getObject("id", UUID.class),
        rs.getObject("patient_id", UUID.class),
        rs.getInt("sequence_number"));
  }

  /** При одинаковом телефоне у нескольких карточек не выбираем пациента за пользователя. */
  private UUID uniquePatient(String phone) {
    String normalized = normalize(phone);
    if (normalized.isEmpty()) return null;
    List<UUID> matches = patientContacts.exactPhoneOwners(clinicId(), normalized);
    return matches.size() == 1 ? matches.get(0) : null;
  }

  private Instant eventTime(JsonNode root) {
    long seconds = root.path("timestamp").asLong(0);
    return seconds > 0 ? Instant.ofEpochSecond(seconds) : Instant.now();
  }

  private static String required(JsonNode root, String field) {
    String value = text(root, field);
    if (value.isEmpty()) throw new IllegalArgumentException("Missing " + field);
    return value;
  }

  private static String text(JsonNode root, String field) {
    JsonNode value = root.path(field);
    return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
  }

  private static String normalize(String phone) {
    if (phone == null) return "";
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() == 11 && digits.startsWith("8")) digits = "7" + digits.substring(1);
    return digits;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private record CallRow(UUID id, UUID patientId, int sequence) {}
}
