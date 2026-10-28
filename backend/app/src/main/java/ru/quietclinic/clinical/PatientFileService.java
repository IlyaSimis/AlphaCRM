package ru.quietclinic.clinical;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.payloadTooLarge;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.EncryptionService;
import ru.quietclinic.platform.Permission;

/**
 * PDF пациента: загрузка, подпись и выгрузка. Файл хранится только зашифрованным.
 *
 * <ul>
 *   <li>Загрузка и подпись — администратор (вся клиника) и врач со связью с лечением.
 *   <li>Черновик скачивают администратор и врач со связью; подписанный — ещё и менеджер.
 *   <li>Кассир файлов не видит. В аудит — только id файла.
 * </ul>
 */
@Service
class PatientFileService {
  /** Предел размера файла; тот же — у multipart в application.yml. */
  static final long MAX_BYTES = 10L * 1024 * 1024;

  private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
  private static final String SELECT =
      "select f.id, f.patient_id, p.full_name as patient_name, f.appointment_id,"
          + " f.original_name, f.size_bytes, f.state, f.content_hash, f.uploaded_by,"
          + " u.full_name as uploaded_by_name, f.uploaded_at, f.signed_by,"
          + " s.full_name as signed_by_name, f.signed_at"
          + " from patient_file f join patient p on p.id = f.patient_id"
          + " left join staff_user u on u.id = f.uploaded_by"
          + " left join staff_user s on s.id = f.signed_by"
          + " where f.clinic_id = ?";

  private final PatientFileRepository files;
  private final CareRelationshipService careRelationships;
  private final EncryptionService encryption;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  PatientFileService(
      PatientFileRepository files,
      CareRelationshipService careRelationships,
      EncryptionService encryption,
      JdbcTemplate jdbc,
      AuditService audit) {
    this.files = files;
    this.careRelationships = careRelationships;
    this.encryption = encryption;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  /** Файлы карточки. Менеджер видит только подписанные, врач — при связи с лечением. */
  @Transactional(readOnly = true)
  List<PatientFileView> list(UUID patientId) {
    requirePermission(READ, READ_SIGNED, WRITE_CARE);
    requirePatient(patientId);
    if (careOnly()) requireCare(patientId);
    var sql = SELECT + " and f.patient_id = ?";
    if (signedOnly()) sql += " and f.state = '" + PatientFile.SIGNED + "'";
    return jdbc.query(sql + " order by f.uploaded_at desc", this::row, clinicId(), patientId);
  }

  /** Архив подписанных PDF: врач — подписанные им самим, менеджер — все файлы клиники. */
  @Transactional(readOnly = true)
  List<PatientFileView> signedArchive() {
    requirePermission(READ, READ_SIGNED, WRITE_CARE);
    var sql = SELECT + " and f.state = '" + PatientFile.SIGNED + "'";
    var args = new ArrayList<Object>(List.of(clinicId()));
    if (careOnly()) {
      sql += " and f.signed_by = ?";
      args.add(actorId());
    }
    return jdbc.query(sql + " order by f.signed_at desc", this::row, args.toArray());
  }

  @Transactional
  PatientFileView upload(UUID patientId, UUID appointmentId, String filename, byte[] content) {
    requirePermission(Permission.PATIENT_FILE_WRITE, WRITE_CARE);
    requirePatient(patientId);
    if (!can(Permission.PATIENT_FILE_WRITE)) requireCare(patientId);
    if (appointmentId != null) requireAppointment(appointmentId, patientId);
    if (content.length > MAX_BYTES) {
      throw payloadTooLarge("FILE_TOO_LARGE", "The file is larger than 10 MB");
    }
    if (!isPdf(content)) throw conflict("NOT_A_PDF", "Only PDF files can be uploaded");
    var file =
        files.saveAndFlush(
            new PatientFile(
                clinicId(),
                patientId,
                appointmentId,
                cleanName(filename),
                content.length,
                encryption.encryptBytes(content),
                actorId(),
                Instant.now()));
    audit.record(actorId(), "PDF_UPLOADED", "PATIENT_FILE", file.id, "ALLOWED", null);
    return view(file.id);
  }

  /** Хеш считается по открытым байтам файла в момент подписи. */
  @Transactional
  PatientFileView sign(UUID fileId) {
    requirePermission(Permission.PATIENT_FILE_WRITE, WRITE_CARE);
    var file = find(fileId);
    if (!can(Permission.PATIENT_FILE_WRITE)) requireCare(file.patientId);
    if (file.signed()) throw conflict("ALREADY_SIGNED", "The file is already signed");
    file.sign(actorId(), sha256(encryption.decryptBytes(file.ciphertext)), Instant.now());
    files.saveAndFlush(file);
    audit.record(actorId(), "PDF_SIGNED", "PATIENT_FILE", file.id, "ALLOWED", null);
    return view(file.id);
  }

  @Transactional(readOnly = true)
  FileDownload download(UUID fileId) {
    requirePermission(READ, READ_SIGNED, WRITE_CARE);
    var file = find(fileId);
    if (!file.signed() && signedOnly()) {
      throw forbidden("FILE_NOT_SIGNED", "Only signed files are available to this role");
    }
    // Подписанный им самим файл врач открывает из архива и без текущей связи с лечением.
    if (careOnly() && !(file.signed() && actorId().equals(file.signedBy))) {
      requireCare(file.patientId);
    }
    var content = encryption.decryptBytes(file.ciphertext);
    audit.record(actorId(), "PDF_READ", "PATIENT_FILE", file.id, "ALLOWED", null);
    return new FileDownload(file.originalName, content);
  }

  private static final Permission READ = Permission.PATIENT_FILE_READ;
  private static final Permission READ_SIGNED = Permission.PATIENT_FILE_READ_SIGNED;
  private static final Permission WRITE_CARE = Permission.PATIENT_FILE_WRITE_CARE;

  /** Врач: только пациенты по связи с лечением и подписанные им самим файлы. */
  private static boolean careOnly() {
    return !can(READ) && !can(READ_SIGNED);
  }

  /** Менеджер: только подписанные файлы. */
  private static boolean signedOnly() {
    return !can(READ) && can(READ_SIGNED);
  }

  static boolean isPdf(byte[] content) {
    if (content.length < PDF_MAGIC.length) return false;
    for (var i = 0; i < PDF_MAGIC.length; i++) {
      if (content[i] != PDF_MAGIC[i]) return false;
    }
    return true;
  }

  /** Имя без пути и управляющих символов, не длиннее 255. */
  static String cleanName(String filename) {
    var name = filename == null ? "" : filename;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("\\p{Cntrl}", "").trim();
    if (name.isEmpty()) name = "document.pdf";
    return name.length() > 255 ? name.substring(0, 255) : name;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private PatientFile find(UUID fileId) {
    return files
        .findByIdAndClinicId(fileId, clinicId())
        .orElseThrow(() -> notFound("FILE_NOT_FOUND", "File not found"));
  }

  private PatientFileView view(UUID fileId) {
    return jdbc.query(SELECT + " and f.id = ?", this::row, clinicId(), fileId).stream()
        .findFirst()
        .orElseThrow(() -> notFound("FILE_NOT_FOUND", "File not found"));
  }

  /** Пациент другой клиники выглядит как несуществующий. */
  private void requirePatient(UUID patientId) {
    var count =
        jdbc.queryForObject(
            "select count(*) from patient where id = ? and clinic_id = ?",
            Long.class,
            patientId,
            clinicId());
    if (count == null || count == 0) throw notFound("PATIENT_NOT_FOUND", "Patient not found");
  }

  private void requireAppointment(UUID appointmentId, UUID patientId) {
    var count =
        jdbc.queryForObject(
            "select count(*) from appointment a join practitioner p on p.id = a.practitioner_id"
                + " where a.id = ? and a.patient_id = ? and p.clinic_id = ?",
            Long.class,
            appointmentId,
            patientId,
            clinicId());
    if (count == null || count == 0) {
      throw notFound("APPOINTMENT_NOT_FOUND", "Appointment not found");
    }
  }

  /** Тот же отказ и та же запись аудита, что при чтении клинических данных без связи. */
  private void requireCare(UUID patientId) {
    if (!careRelationships.isActive(actorId(), patientId)) {
      audit.record(
          actorId(), "CLINICAL_ACCESS", "PATIENT", patientId, "DENIED", "no-care-relationship");
      throw forbidden("NO_CARE_RELATIONSHIP", "No active care relationship with the patient");
    }
  }

  private PatientFileView row(ResultSet rs, int index) throws SQLException {
    return new PatientFileView(
        rs.getObject("id", UUID.class),
        rs.getObject("patient_id", UUID.class),
        rs.getString("patient_name"),
        rs.getObject("appointment_id", UUID.class),
        rs.getString("original_name"),
        rs.getLong("size_bytes"),
        rs.getString("state"),
        rs.getString("content_hash"),
        rs.getObject("uploaded_by", UUID.class),
        rs.getString("uploaded_by_name"),
        instant(rs, "uploaded_at"),
        rs.getObject("signed_by", UUID.class),
        rs.getString("signed_by_name"),
        instant(rs, "signed_at"));
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    var value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
