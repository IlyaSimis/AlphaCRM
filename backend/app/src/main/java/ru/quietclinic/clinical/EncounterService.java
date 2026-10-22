package ru.quietclinic.clinical;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.EncryptionService;

/**
 * Клинический контур приёма: старт по записи, заметка, документ, подпись и дополнения. Текст
 * заметки расшифровывается только после проверки связи с лечением.
 */
@Service
class EncounterService {
  static final Set<String> STARTABLE_STATUSES = Set.of("ARRIVED", "IN_PROGRESS");

  private final EncounterRepository encounters;
  private final ClinicalNoteRepository notes;
  private final MedicalDocumentRepository documents;
  private final CareRelationshipService careRelationships;
  private final VisitAppointments appointments;
  private final EncryptionService encryption;
  private final AuditService audit;

  EncounterService(
      EncounterRepository encounters,
      ClinicalNoteRepository notes,
      MedicalDocumentRepository documents,
      CareRelationshipService careRelationships,
      VisitAppointments appointments,
      EncryptionService encryption,
      AuditService audit) {
    this.encounters = encounters;
    this.notes = notes;
    this.documents = documents;
    this.careRelationships = careRelationships;
    this.appointments = appointments;
    this.encryption = encryption;
    this.audit = audit;
  }

  @Transactional
  EncounterStart start(StartEncounterRequest request) {
    requireRole("DOCTOR");
    if (!actorId().equals(request.practitionerId())) {
      throw forbidden("Врач может начать только собственный приём");
    }
    var appointment =
        appointments
            .find(request.appointmentId())
            .orElseThrow(() -> notFound("Запись не найдена"));
    if (!appointment.patientId().equals(request.patientId())
        || !appointment.practitionerId().equals(request.practitionerId())) {
      throw conflict("APPOINTMENT_MISMATCH", "Пациент или врач не совпадают с записью");
    }
    if (!STARTABLE_STATUSES.contains(appointment.status())) {
      throw conflict(
          "APPOINTMENT_NOT_ARRIVED", "Приём можно начать, когда пациент пришёл на запись");
    }
    var existing = encounters.findFirstByAppointmentIdOrderByStartedAtAsc(request.appointmentId());
    if (existing.isPresent()) return new EncounterStart(view(existing.get(), null), false);
    var encounter =
        encounters.save(
            new Encounter(request.appointmentId(), request.patientId(), request.practitionerId()));
    audit.record(actorId(), "ENCOUNTER_STARTED", "ENCOUNTER", encounter.id, "ALLOWED", "treatment");
    return new EncounterStart(view(encounter, null), true);
  }

  @Transactional(readOnly = true)
  List<ClinicalView> history(UUID patientId) {
    requireClinicalAccess(patientId);
    var result =
        encounters.findByPatientIdOrderByStartedAtDesc(patientId).stream()
            .map(e -> view(e, notes.findByEncounterId(e.id).orElse(null)))
            .toList();
    audit.record(actorId(), "CLINICAL_HISTORY_READ", "PATIENT", patientId, "ALLOWED", "treatment");
    return result;
  }

  @Transactional
  ClinicalView saveNote(UUID encounterId, ClinicalNoteRequest request) {
    requireRole("DOCTOR");
    var encounter = findEncounter(encounterId);
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

  @Transactional
  DocumentView createDocument(UUID encounterId, DocumentRequest request) {
    requireRole("DOCTOR");
    var encounter = findEncounter(encounterId);
    requireClinicalAccess(encounter.patientId);
    var content = contentOf(encounterId, request);
    var doc =
        documents.save(
            new MedicalDocument(
                encounterId, null, request.templateVersion(), encryption.encrypt(content)));
    audit.record(actorId(), "DOCUMENT_DRAFT_CREATED", "DOCUMENT", doc.id, "ALLOWED", "treatment");
    return DocumentView.of(doc);
  }

  /**
   * Подписывает черновик. Если у приёма есть заметка, черновик сначала приводится к её текущему
   * содержимому — подписывается ровно то, что врач видит в форме.
   */
  @Transactional
  DocumentView sign(UUID documentId) {
    requireRole("DOCTOR");
    var doc = findDocument(documentId);
    var encounter = findEncounter(doc.encounterId);
    requireClinicalAccess(encounter.patientId);
    var note = notes.findByEncounterId(encounter.id);
    String plaintext;
    if (note.isPresent() && "DRAFT".equals(doc.state)) {
      plaintext = canonical(note.get());
      doc.updateSnapshot(encryption.encrypt(plaintext));
    } else {
      plaintext = encryption.decrypt(doc.snapshotEncrypted);
    }
    doc.sign(actorId(), plaintext);
    documents.saveAndFlush(doc);
    audit.record(actorId(), "DOCUMENT_SIGNED", "DOCUMENT", doc.id, "ALLOWED", "attestation");
    return DocumentView.of(doc);
  }

  @Transactional
  DocumentView amend(UUID documentId, DocumentRequest request) {
    requireRole("DOCTOR");
    var original = findDocument(documentId);
    if (!original.state.equals("SIGNED_LOCKED")) {
      throw conflict("ORIGINAL_NOT_SIGNED", "Дополнение возможно только к подписанному документу");
    }
    var encounter = findEncounter(original.encounterId);
    requireClinicalAccess(encounter.patientId);
    var amendment =
        documents.save(
            new MedicalDocument(
                original.encounterId,
                original.id,
                request.templateVersion(),
                encryption.encrypt(contentOf(original.encounterId, request))));
    audit.record(
        actorId(), "DOCUMENT_AMENDMENT_CREATED", "DOCUMENT", amendment.id, "ALLOWED", "correction");
    return DocumentView.of(amendment);
  }

  private String contentOf(UUID encounterId, DocumentRequest request) {
    if (request.canonicalContent() != null && !request.canonicalContent().isBlank()) {
      return request.canonicalContent();
    }
    var note =
        notes
            .findByEncounterId(encounterId)
            .orElseThrow(
                () -> conflict("NOTE_REQUIRED", "Сначала сохраните черновик заметки приёма"));
    return canonical(note);
  }

  private String canonical(ClinicalNote note) {
    return NoteContent.canonical(
        encryption.decrypt(note.complaintsEncrypted),
        encryption.decrypt(note.mentalStatusEncrypted),
        encryption.decrypt(note.diagnosisEncrypted),
        encryption.decrypt(note.recommendationsEncrypted));
  }

  private Encounter findEncounter(UUID id) {
    return encounters.findById(id).orElseThrow(() -> notFound("Приём не найден"));
  }

  private MedicalDocument findDocument(UUID id) {
    return documents.findById(id).orElseThrow(() -> notFound("Документ не найден"));
  }

  private void requireClinicalAccess(UUID patientId) {
    requireRole("DOCTOR", "NURSE");
    if (!careRelationships.isActive(actorId(), patientId)) {
      audit.record(
          actorId(), "CLINICAL_ACCESS", "PATIENT", patientId, "DENIED", "no-care-relationship");
      throw forbidden("Нет активной связи с лечением пациента");
    }
  }

  private ClinicalView view(Encounter e, ClinicalNote n) {
    return new ClinicalView(
        e.id,
        e.appointmentId,
        e.patientId,
        e.practitionerId,
        e.status,
        e.startedAt,
        n == null ? null : encryption.decrypt(n.complaintsEncrypted),
        n == null ? null : encryption.decrypt(n.mentalStatusEncrypted),
        n == null ? null : encryption.decrypt(n.diagnosisEncrypted),
        n == null ? null : encryption.decrypt(n.recommendationsEncrypted),
        n == null ? null : n.version,
        documents.findByEncounterIdOrderByCreatedAtAsc(e.id).stream()
            .map(DocumentSummary::of)
            .toList());
  }
}
