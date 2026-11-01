package ru.quietclinic.clinical;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.EncryptionService;
import ru.quietclinic.platform.Permission;

/**
 * Архив подписанных документов, только чтение. Врач видит документы, которые подписал сам; менеджер
 * — все документы своей клиники. Связь с лечением здесь не нужна: доступ даёт роль и факт подписи.
 * В аудит пишется факт чтения без текста документа.
 */
@Service
class SignedDocumentService {
  static final String SIGNED = "SIGNED_LOCKED";

  private final MedicalDocumentRepository documents;
  private final EncounterRepository encounters;
  private final EncryptionService encryption;
  private final JdbcTemplate jdbc;
  private final AuditService audit;

  SignedDocumentService(
      MedicalDocumentRepository documents,
      EncounterRepository encounters,
      EncryptionService encryption,
      JdbcTemplate jdbc,
      AuditService audit) {
    this.documents = documents;
    this.encounters = encounters;
    this.encryption = encryption;
    this.jdbc = jdbc;
    this.audit = audit;
  }

  /** Менеджер может сузить список до одного врача; врачу фильтр не нужен — он видит только своё. */
  @Transactional(readOnly = true)
  List<SignedDocumentView> list(UUID practitionerId) {
    requirePermission(Permission.SIGNED_DOCUMENT_READ, Permission.SIGNED_DOCUMENT_READ_OWN);
    List<MedicalDocument> rows =
        !can(Permission.SIGNED_DOCUMENT_READ)
            ? documents.findByStateAndSignedByOrderBySignedAtDesc(SIGNED, actorId())
            : practitionerId != null
                ? documents.findByStateAndSignedByOrderBySignedAtDesc(SIGNED, practitionerId)
                : documents.findByStateOrderBySignedAtDesc(SIGNED);
    List<SignedDocumentView> result = views(rows);
    result.forEach(
        d ->
            audit.record(actorId(), "SIGNED_DOCUMENT_READ", "DOCUMENT", d.id(), "ALLOWED", "list"));
    return result;
  }

  @Transactional(readOnly = true)
  SignedDocumentView get(UUID id) {
    requirePermission(Permission.SIGNED_DOCUMENT_READ, Permission.SIGNED_DOCUMENT_READ_OWN);
    MedicalDocument doc =
        documents
            .findById(id)
            .filter(d -> SIGNED.equals(d.state))
            .filter(d -> can(Permission.SIGNED_DOCUMENT_READ) || actorId().equals(d.signedBy))
            .orElseThrow(() -> notFound("SIGNED_DOCUMENT_NOT_FOUND", "Signed document not found"));
    SignedDocumentView view =
        views(List.of(doc)).stream()
            .findFirst()
            .orElseThrow(() -> notFound("SIGNED_DOCUMENT_NOT_FOUND", "Signed document not found"));
    audit.record(actorId(), "SIGNED_DOCUMENT_READ", "DOCUMENT", doc.id, "ALLOWED", "card");
    return view;
  }

  /** Только документы врачей своей клиники; имена — из таблиц, без зависимости от кода модулей. */
  private List<SignedDocumentView> views(List<MedicalDocument> rows) {
    Map<UUID, String> doctors = names("select id, full_name from practitioner where clinic_id = ?");
    Map<UUID, String> patients = names("select id, full_name from patient where clinic_id = ?");
    Map<UUID, Encounter> byId =
        encounters.findAllById(rows.stream().map(d -> d.encounterId).distinct().toList()).stream()
            .collect(Collectors.toMap(e -> e.id, Function.identity()));
    return rows.stream()
        .filter(d -> d.signedBy != null && doctors.containsKey(d.signedBy))
        .filter(d -> byId.containsKey(d.encounterId))
        .map(
            d -> {
              Encounter encounter = byId.get(d.encounterId);
              SnapshotFields fields = NoteContent.parse(encryption.decrypt(d.snapshotEncrypted));
              return new SignedDocumentView(
                  d.id,
                  d.encounterId,
                  encounter.appointmentId,
                  encounter.patientId,
                  patients.get(encounter.patientId),
                  d.signedBy,
                  doctors.get(d.signedBy),
                  d.signedAt,
                  d.templateVersion,
                  d.amendsDocumentId,
                  d.contentHash,
                  fields.complaints(),
                  fields.mentalStatus(),
                  fields.diagnosis(),
                  fields.recommendations());
            })
        .toList();
  }

  private Map<UUID, String> names(String sql) {
    Map<UUID, String> result = new HashMap<>();
    jdbc.query(
        sql,
        (ResultSet row) -> {
          result.put(row.getObject("id", UUID.class), row.getString("full_name"));
        },
        clinicId());
    return result;
  }
}
