package ru.quietclinic.clinical;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.EncryptionService;

@Entity
@Table(name = "care_relationship")
class CareRelationship {
  @Id UUID id;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  UUID patientId;

  @Column(nullable = false)
  Instant validFrom;

  @Column(nullable = false)
  Instant validUntil;

  @Column(nullable = false)
  String reason;

  protected CareRelationship() {}
}

interface CareRelationshipRepository extends JpaRepository<CareRelationship, UUID> {
  boolean existsByPractitionerIdAndPatientIdAndValidFromBeforeAndValidUntilAfter(
      UUID practitionerId, UUID patientId, Instant now1, Instant now2);
}

@Entity
@Table(name = "encounter")
class Encounter {
  @Id UUID id;

  @Column(nullable = false)
  UUID appointmentId;

  @Column(nullable = false)
  UUID patientId;

  @Column(nullable = false)
  UUID practitionerId;

  @Column(nullable = false)
  String status;

  @Column(nullable = false)
  Instant startedAt;

  Instant completedAt;

  protected Encounter() {}

  Encounter(UUID appointmentId, UUID patientId, UUID practitionerId) {
    id = UUID.randomUUID();
    this.appointmentId = appointmentId;
    this.patientId = patientId;
    this.practitionerId = practitionerId;
    status = "IN_PROGRESS";
    startedAt = Instant.now();
  }
}

@Entity
@Table(name = "clinical_note")
class ClinicalNote {
  @Id UUID id;

  @Column(nullable = false, unique = true)
  UUID encounterId;

  @Column(nullable = false, length = 16000)
  String complaintsEncrypted;

  @Column(nullable = false, length = 16000)
  String mentalStatusEncrypted;

  @Column(nullable = false, length = 16000)
  String diagnosisEncrypted;

  @Column(nullable = false, length = 16000)
  String recommendationsEncrypted;

  @Column(nullable = false)
  UUID authorId;

  @Column(nullable = false)
  Instant updatedAt;

  @Version long version;

  protected ClinicalNote() {}
}

@Entity
@Table(name = "medical_document")
class MedicalDocument {
  @Id UUID id;

  @Column(nullable = false)
  UUID encounterId;

  UUID amendsDocumentId;

  @Column(nullable = false)
  String state;

  @Column(nullable = false)
  String templateVersion;

  @Column(nullable = false, length = 32000)
  String snapshotEncrypted;

  String contentHash;
  UUID signedBy;
  Instant signedAt;
  @Version long version;

  protected MedicalDocument() {}

  MedicalDocument(
      UUID encounterId, UUID amendsDocumentId, String templateVersion, String snapshotEncrypted) {
    id = UUID.randomUUID();
    this.encounterId = encounterId;
    this.amendsDocumentId = amendsDocumentId;
    this.templateVersion = templateVersion;
    this.snapshotEncrypted = snapshotEncrypted;
    state = "DRAFT";
  }

  void updateSnapshot(String snapshotEncrypted) {
    if (!state.equals("DRAFT"))
      throw conflict("DOCUMENT_LOCKED", "Подписанный документ неизменяем");
    this.snapshotEncrypted = snapshotEncrypted;
  }

  void sign(UUID signer, String canonicalPlaintext) {
    if (!state.equals("DRAFT")) throw conflict("DOCUMENT_LOCKED", "Документ уже подписан");
    try {
      contentHash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(canonicalPlaintext.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    signedBy = signer;
    signedAt = Instant.now();
    state = "SIGNED_LOCKED";
  }
}

interface EncounterRepository extends JpaRepository<Encounter, UUID> {
  List<Encounter> findByPatientIdOrderByStartedAtDesc(UUID patientId);
}

interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {
  java.util.Optional<ClinicalNote> findByEncounterId(UUID encounterId);
}

interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, UUID> {
  List<MedicalDocument> findByEncounterIdOrderByVersionDesc(UUID encounterId);
}

record StartEncounterRequest(
    @NotNull UUID appointmentId, @NotNull UUID patientId, @NotNull UUID practitionerId) {}

record ClinicalNoteRequest(
    @NotBlank String complaints,
    @NotBlank String mentalStatus,
    @NotBlank String diagnosis,
    @NotBlank String recommendations) {}

record ClinicalView(
    UUID encounterId,
    UUID patientId,
    UUID practitionerId,
    String status,
    String complaints,
    String mentalStatus,
    String diagnosis,
    String recommendations,
    Long noteVersion) {}

record DocumentRequest(@NotBlank String templateVersion, @NotBlank String canonicalContent) {}

record DocumentView(
    UUID id,
    UUID encounterId,
    UUID amendsDocumentId,
    String state,
    String templateVersion,
    String contentHash,
    UUID signedBy,
    Instant signedAt,
    long version) {}

@RestController
@RequestMapping("/api/v1/clinical")
public class ClinicalController {
  private final EncounterRepository encounters;
  private final ClinicalNoteRepository notes;
  private final MedicalDocumentRepository documents;
  private final CareRelationshipRepository relationships;
  private final EncryptionService encryption;
  private final AuditService audit;

  ClinicalController(
      EncounterRepository encounters,
      ClinicalNoteRepository notes,
      MedicalDocumentRepository documents,
      CareRelationshipRepository relationships,
      EncryptionService encryption,
      AuditService audit) {
    this.encounters = encounters;
    this.notes = notes;
    this.documents = documents;
    this.relationships = relationships;
    this.encryption = encryption;
    this.audit = audit;
  }

  @PostMapping("/encounters")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  ClinicalView start(@Valid @RequestBody StartEncounterRequest request) {
    requireRole("DOCTOR");
    if (!actorId().equals(request.practitionerId())) {
      throw forbidden("Врач может начать только собственный приём");
    }
    var encounter =
        encounters.save(
            new Encounter(request.appointmentId(), request.patientId(), request.practitionerId()));
    audit.record(actorId(), "ENCOUNTER_STARTED", "ENCOUNTER", encounter.id, "ALLOWED", "treatment");
    return view(encounter, null);
  }

  @GetMapping("/patients/{patientId}/encounters")
  @Transactional(readOnly = true)
  List<ClinicalView> history(@PathVariable UUID patientId) {
    requireClinicalAccess(patientId);
    var result =
        encounters.findByPatientIdOrderByStartedAtDesc(patientId).stream()
            .map(e -> view(e, notes.findByEncounterId(e.id).orElse(null)))
            .toList();
    audit.record(actorId(), "CLINICAL_HISTORY_READ", "PATIENT", patientId, "ALLOWED", "treatment");
    return result;
  }

  @PutMapping("/encounters/{encounterId}/note")
  @Transactional
  ClinicalView saveNote(
      @PathVariable UUID encounterId, @Valid @RequestBody ClinicalNoteRequest request) {
    requireRole("DOCTOR");
    var encounter = encounters.findById(encounterId).orElseThrow();
    requireClinicalAccess(encounter.patientId);
    var note = notes.findByEncounterId(encounterId).orElseGet(ClinicalNote::new);
    if (note.id == null) {
      note.id = UUID.randomUUID();
      note.encounterId = encounterId;
    }
    note.complaintsEncrypted = encryption.encrypt(request.complaints());
    note.mentalStatusEncrypted = encryption.encrypt(request.mentalStatus());
    note.diagnosisEncrypted = encryption.encrypt(request.diagnosis());
    note.recommendationsEncrypted = encryption.encrypt(request.recommendations());
    note.authorId = actorId();
    note.updatedAt = Instant.now();
    notes.save(note);
    audit.record(
        actorId(), "CLINICAL_NOTE_SAVED", "ENCOUNTER", encounterId, "ALLOWED", "treatment");
    return view(encounter, note);
  }

  @PostMapping("/encounters/{encounterId}/documents")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  DocumentView createDocument(
      @PathVariable UUID encounterId, @Valid @RequestBody DocumentRequest request) {
    requireRole("DOCTOR");
    var encounter = encounters.findById(encounterId).orElseThrow();
    requireClinicalAccess(encounter.patientId);
    var doc =
        documents.save(
            new MedicalDocument(
                encounterId,
                null,
                request.templateVersion(),
                encryption.encrypt(request.canonicalContent())));
    audit.record(actorId(), "DOCUMENT_DRAFT_CREATED", "DOCUMENT", doc.id, "ALLOWED", "treatment");
    return documentView(doc);
  }

  @PostMapping("/documents/{documentId}/sign")
  @Transactional
  DocumentView sign(@PathVariable UUID documentId) {
    requireRole("DOCTOR");
    var doc = documents.findById(documentId).orElseThrow();
    var encounter = encounters.findById(doc.encounterId).orElseThrow();
    requireClinicalAccess(encounter.patientId);
    var plaintext = encryption.decrypt(doc.snapshotEncrypted);
    doc.sign(actorId(), plaintext);
    documents.saveAndFlush(doc);
    audit.record(actorId(), "DOCUMENT_SIGNED", "DOCUMENT", doc.id, "ALLOWED", "attestation");
    return documentView(doc);
  }

  @PostMapping("/documents/{documentId}/amendments")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  DocumentView amend(@PathVariable UUID documentId, @Valid @RequestBody DocumentRequest request) {
    requireRole("DOCTOR");
    var original = documents.findById(documentId).orElseThrow();
    if (!original.state.equals("SIGNED_LOCKED")) {
      throw conflict("ORIGINAL_NOT_SIGNED", "Дополнение возможно только к подписанному документу");
    }
    var encounter = encounters.findById(original.encounterId).orElseThrow();
    requireClinicalAccess(encounter.patientId);
    var amendment =
        documents.save(
            new MedicalDocument(
                original.encounterId,
                original.id,
                request.templateVersion(),
                encryption.encrypt(request.canonicalContent())));
    audit.record(
        actorId(), "DOCUMENT_AMENDMENT_CREATED", "DOCUMENT", amendment.id, "ALLOWED", "correction");
    return documentView(amendment);
  }

  private void requireClinicalAccess(UUID patientId) {
    requireRole("DOCTOR", "NURSE");
    var now = Instant.now();
    if (!relationships.existsByPractitionerIdAndPatientIdAndValidFromBeforeAndValidUntilAfter(
        actorId(), patientId, now, now)) {
      audit.record(
          actorId(), "CLINICAL_ACCESS", "PATIENT", patientId, "DENIED", "no-care-relationship");
      throw forbidden("Нет активной связи с лечением пациента");
    }
  }

  private ClinicalView view(Encounter e, ClinicalNote n) {
    return new ClinicalView(
        e.id,
        e.patientId,
        e.practitionerId,
        e.status,
        n == null ? null : encryption.decrypt(n.complaintsEncrypted),
        n == null ? null : encryption.decrypt(n.mentalStatusEncrypted),
        n == null ? null : encryption.decrypt(n.diagnosisEncrypted),
        n == null ? null : encryption.decrypt(n.recommendationsEncrypted),
        n == null ? null : n.version);
  }

  private DocumentView documentView(MedicalDocument d) {
    return new DocumentView(
        d.id,
        d.encounterId,
        d.amendsDocumentId,
        d.state,
        d.templateVersion,
        d.contentHash,
        d.signedBy,
        d.signedAt,
        d.version);
  }
}
