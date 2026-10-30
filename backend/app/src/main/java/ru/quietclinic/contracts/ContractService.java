package ru.quietclinic.contracts;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.payloadTooLarge;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.contracts.ContractController.ContractView;
import ru.quietclinic.contracts.ContractController.CreateContractRequest;
import ru.quietclinic.contracts.ContractController.TemplateView;
import ru.quietclinic.patients.PatientTemplateValues;
import ru.quietclinic.platform.EncryptionService;
import ru.quietclinic.platform.Permission;
import tools.jackson.databind.json.JsonMapper;

/** Версии DOCX-шаблонов и неизменяемые договоры пациентов. */
@Service
class ContractService {
  private static final int MAX_TEMPLATE_BYTES = 5 * 1024 * 1024;
  private static final Pattern CUSTOM_KEY = Pattern.compile("[a-z][a-zA-Z0-9_.-]{0,79}");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final JdbcTemplate jdbc;
  private final EncryptionService encryption;
  private final PatientTemplateValues patientValues;
  private final LibreOfficePdfConverter pdf;
  private final AuditService audit;
  private final JsonMapper json;
  private final ZoneId clinicZone;

  ContractService(
      JdbcTemplate jdbc,
      EncryptionService encryption,
      PatientTemplateValues patientValues,
      LibreOfficePdfConverter pdf,
      AuditService audit,
      JsonMapper json,
      @Value("${clinic.time-zone:UTC}") String clinicZone) {
    this.jdbc = jdbc;
    this.encryption = encryption;
    this.patientValues = patientValues;
    this.pdf = pdf;
    this.audit = audit;
    this.json = json;
    this.clinicZone = ZoneId.of(clinicZone);
  }

  @Transactional(readOnly = true)
  List<TemplateView> templates() {
    requirePermission(Permission.CONTRACT_USE);
    return jdbc.query(
        "select id, name, original_name, version, placeholders, created_at from contract_template"
            + " where clinic_id = ? order by name, version desc",
        (rs, row) ->
            new TemplateView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("original_name"),
                rs.getInt("version"),
                List.of(rs.getString("placeholders").split(",")),
                rs.getTimestamp("created_at").toInstant()),
        clinicId());
  }

  @Transactional(readOnly = true)
  List<ContractController.ContractPatientView> patients() {
    requirePermission(Permission.CONTRACT_USE);
    return jdbc.query(
        "select id, full_name from patient where clinic_id = ? order by full_name",
        (rs, row) ->
            new ContractController.ContractPatientView(
                rs.getObject("id", UUID.class), rs.getString("full_name")),
        clinicId());
  }

  @Transactional
  TemplateView uploadTemplate(String name, String originalName, byte[] content) {
    requirePermission(Permission.PATIENT_FILE_WRITE);
    var cleanName = name == null ? "" : name.trim();
    if (cleanName.isEmpty() || cleanName.length() > 200) {
      throw conflict(
          "INVALID_CONTRACT_TEMPLATE_NAME", "Enter a template name up to 200 characters");
    }
    if (content == null || content.length == 0 || content.length > MAX_TEMPLATE_BYTES) {
      throw payloadTooLarge("CONTRACT_TEMPLATE_TOO_LARGE", "DOCX template must be 1 byte to 5 MB");
    }
    var placeholders = DocxTemplate.placeholders(content);
    if (placeholders.isEmpty()) {
      throw conflict("CONTRACT_TEMPLATE_VARIABLES_MISSING", "DOCX template has no {{variables}}");
    }
    var id = UUID.randomUUID();
    var now = Instant.now();
    var version =
        jdbc.queryForObject(
            "select coalesce(max(version), 0) + 1 from contract_template where clinic_id = ? and name = ?",
            Integer.class,
            clinicId(),
            cleanName);
    jdbc.update(
        "insert into contract_template(id, clinic_id, name, original_name, version, placeholders,"
            + " docx_ciphertext, created_by, created_at) values(?,?,?,?,?,?,?,?,?)",
        id,
        clinicId(),
        cleanName,
        cleanFilename(originalName),
        version,
        String.join(",", placeholders),
        encryption.encryptBytes(content),
        actorId(),
        Timestamp.from(now));
    audit.record(actorId(), "CONTRACT_TEMPLATE_CREATED", "CONTRACT_TEMPLATE", id, "ALLOWED", null);
    return new TemplateView(
        id, cleanName, cleanFilename(originalName), version, List.copyOf(placeholders), now);
  }

  @Transactional(readOnly = true)
  List<ContractView> list(UUID patientId) {
    requirePermission(Permission.CONTRACT_USE);
    requirePatient(patientId);
    return jdbc.query(
        "select c.id, c.patient_id, c.contract_number, t.name as template_name, c.service_name,"
            + " c.pdf_ciphertext is not null as pdf_available, c.created_at, u.full_name as created_by_name"
            + " from patient_contract c join contract_template t on t.id = c.template_id"
            + " left join staff_user u on u.id = c.created_by"
            + " where c.clinic_id = ? and c.patient_id = ? order by c.created_at desc",
        this::contractView,
        clinicId(),
        patientId);
  }

  @Transactional
  ContractView create(UUID patientId, CreateContractRequest request) {
    requirePermission(Permission.CONTRACT_USE);
    var automatic = patientValues.forPatient(patientId, clinicId());
    if (automatic == null) throw notFound("PATIENT_NOT_FOUND", "Patient not found");
    var template = template(request.templateId());
    var now = Instant.now();
    var today = LocalDate.now(clinicZone);
    automatic.put("contract.date", DATE.format(today));
    automatic.put("contract.number", "");
    automatic.put("clinic.name", clinicName());
    if (request.serviceId() != null) automatic.putAll(serviceValues(request.serviceId()));
    var values = new HashMap<>(automatic);
    if (request.values() != null) {
      request
          .values()
          .forEach(
              (key, value) -> {
                if (key == null
                    || !CUSTOM_KEY.matcher(key).matches()
                    || value == null
                    || value.length() > 5000) {
                  throw conflict("INVALID_CONTRACT_VALUE", "Check custom contract fields");
                }
                values.putIfAbsent(key, value);
                if (values.get(key).isEmpty()) values.put(key, value);
              });
    }
    var missing = template.placeholders().stream().filter(key -> !values.containsKey(key)).toList();
    if (!missing.isEmpty()) {
      throw conflict("CONTRACT_VALUES_REQUIRED", "Missing values: " + String.join(",", missing));
    }
    var id = UUID.randomUUID();
    var number = nextNumber(today);
    values.put("contract.number", number);
    var used = new HashMap<String, String>();
    template.placeholders().forEach(key -> used.put(key, values.get(key)));
    var docx = DocxTemplate.render(encryption.decryptBytes(template.docx()), used);
    var pdfContent = pdf.convert(docx);
    var snapshot = json.writeValueAsString(used);
    jdbc.update(
        "insert into patient_contract(id, clinic_id, patient_id, template_id, service_id,"
            + " contract_number, service_name, snapshot_values, docx_ciphertext, pdf_ciphertext,"
            + " docx_hash, pdf_hash, created_by, created_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        clinicId(),
        patientId,
        template.id(),
        request.serviceId(),
        number,
        values.get("service.name"),
        encryption.encrypt(snapshot, "patient-contract:" + clinicId() + ":" + id + ":snapshot"),
        encryption.encryptBytes(docx),
        pdfContent.map(encryption::encryptBytes).orElse(null),
        sha256(docx),
        pdfContent.map(ContractService::sha256).orElse(null),
        actorId(),
        Timestamp.from(now));
    audit.record(actorId(), "PATIENT_CONTRACT_CREATED", "PATIENT_CONTRACT", id, "ALLOWED", null);
    return new ContractView(
        id,
        patientId,
        number,
        template.name(),
        values.get("service.name"),
        pdfContent.isPresent(),
        now,
        null);
  }

  @Transactional(readOnly = true)
  ContractFile download(UUID id, boolean pdfRequested) {
    requirePermission(Permission.CONTRACT_USE);
    var rows =
        jdbc.query(
            "select contract_number, docx_ciphertext, pdf_ciphertext from patient_contract"
                + " where id = ? and clinic_id = ?",
            (rs, row) ->
                new StoredFile(
                    rs.getString("contract_number"),
                    rs.getBytes("docx_ciphertext"),
                    rs.getBytes("pdf_ciphertext")),
            id,
            clinicId());
    if (rows.isEmpty()) throw notFound("CONTRACT_NOT_FOUND", "Contract not found");
    var file = rows.get(0);
    if (pdfRequested && file.pdf() == null) {
      throw notFound("CONTRACT_PDF_NOT_FOUND", "PDF was not generated for this contract");
    }
    return new ContractFile(
        file.number() + (pdfRequested ? ".pdf" : ".docx"),
        pdfRequested
            ? "application/pdf"
            : "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        pdfRequested ? encryption.decryptBytes(file.pdf()) : encryption.decryptBytes(file.docx()));
  }

  private TemplateRow template(UUID id) {
    var rows =
        jdbc.query(
            "select id, name, placeholders, docx_ciphertext from contract_template"
                + " where id = ? and clinic_id = ?",
            (rs, row) ->
                new TemplateRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    List.of(rs.getString("placeholders").split(",")),
                    rs.getBytes("docx_ciphertext")),
            id,
            clinicId());
    if (rows.isEmpty())
      throw notFound("CONTRACT_TEMPLATE_NOT_FOUND", "Contract template not found");
    return rows.get(0);
  }

  private Map<String, String> serviceValues(UUID serviceId) {
    var rows =
        jdbc.query(
            "select name, duration_minutes, price_minor, currency from clinic_service"
                + " where id = ? and clinic_id = ? and active = true",
            (rs, row) -> {
              var result = new HashMap<String, String>();
              result.put("service.name", rs.getString("name"));
              result.put(
                  "service.durationMinutes", Integer.toString(rs.getInt("duration_minutes")));
              var price = rs.getBigDecimal("price_minor");
              result.put(
                  "service.price", price == null ? "" : money(price.longValueExact()) + " ₽");
              result.put("service.currency", rs.getString("currency"));
              return result;
            },
            serviceId,
            clinicId());
    if (rows.isEmpty()) throw notFound("SERVICE_NOT_FOUND", "Active service not found");
    return rows.get(0);
  }

  private String clinicName() {
    return jdbc.queryForObject("select name from clinic where id = ?", String.class, clinicId());
  }

  private void requirePatient(UUID patientId) {
    var count =
        jdbc.queryForObject(
            "select count(*) from patient where id = ? and clinic_id = ?",
            Long.class,
            patientId,
            clinicId());
    if (count == null || count == 0) throw notFound("PATIENT_NOT_FOUND", "Patient not found");
  }

  private String nextNumber(LocalDate today) {
    var year = today.getYear();
    var current = lockedCounter();
    if (current.isEmpty()) {
      try {
        jdbc.update(
            "insert into contract_counter(clinic_id, last_number) values(?, 0)", clinicId());
      } catch (DuplicateKeyException ignored) {
        // Параллельная транзакция создала счётчик: блокируем и читаем уже её строку.
      }
      current = lockedCounter();
    }
    var updated = current.get(0) + 1;
    jdbc.update(
        "update contract_counter set last_number = ? where clinic_id = ?", updated, clinicId());
    return "Д-" + year + "-" + String.format("%05d", updated);
  }

  private List<Long> lockedCounter() {
    return jdbc.query(
        "select last_number from contract_counter where clinic_id = ? for update",
        (rs, row) -> rs.getLong(1),
        clinicId());
  }

  private ContractView contractView(ResultSet rs, int row) throws SQLException {
    return new ContractView(
        rs.getObject("id", UUID.class),
        rs.getObject("patient_id", UUID.class),
        rs.getString("contract_number"),
        rs.getString("template_name"),
        rs.getString("service_name"),
        rs.getBoolean("pdf_available"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getString("created_by_name"));
  }

  private static String cleanFilename(String value) {
    var name = value == null ? "template.docx" : value.replaceAll("[\\r\\n\\\\/]", "_").trim();
    return name.isEmpty() ? "template.docx" : name.length() > 255 ? name.substring(0, 255) : name;
  }

  private static String money(long minor) {
    return java.math.BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString();
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private record TemplateRow(UUID id, String name, List<String> placeholders, byte[] docx) {}

  private record StoredFile(String number, byte[] docx, byte[] pdf) {}

  record ContractFile(String filename, String mediaType, byte[] content) {}
}
