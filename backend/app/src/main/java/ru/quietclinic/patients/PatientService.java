package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;

/** Административные данные пациента: поиск, регистрация, карточка и редактирование. */
@Service
class PatientService {
  private final PatientRepository patients;
  private final AuditService audit;

  PatientService(PatientRepository patients, AuditService audit) {
    this.patients = patients;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  List<PatientView> search(String query) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var result =
        (query.isBlank()
                ? patients.findAll()
                : patients.findTop20ByFullNameContainingIgnoreCaseOrderByFullName(query))
            .stream().map(PatientView::of).toList();
    result.forEach(
        p -> audit.record(actorId(), "PATIENT_ADMIN_READ", "PATIENT", p.id(), "ALLOWED", "search"));
    return result;
  }

  @Transactional
  PatientView create(CreatePatientRequest request) {
    requireRole("ADMINISTRATOR");
    var patient = new Patient(request.fullName(), request.birthDate());
    patient.contacts.add(new Contact(patient, "PHONE", request.phone()));
    patient.consents.add(new Consent(patient, request.consentVersion(), request.consentBasis()));
    patients.save(patient);
    audit.record(actorId(), "PATIENT_CREATED", "PATIENT", patient.id, "ALLOWED", "registration");
    return PatientView.of(patient);
  }

  @Transactional(readOnly = true)
  PatientView get(UUID id) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var patient = find(id);
    audit.record(actorId(), "PATIENT_ADMIN_READ", "PATIENT", patient.id, "ALLOWED", "card");
    return PatientView.of(patient);
  }

  @Transactional
  PatientView update(UUID id, UpdatePatientRequest request) {
    requireRole("ADMINISTRATOR");
    var patient = find(id);
    var changed = patient.update(request.fullName(), request.birthDate(), request.phone());
    patients.save(patient);
    // В аудит пишутся только имена изменённых полей, без значений.
    audit.record(
        actorId(),
        "PATIENT_UPDATED",
        "PATIENT",
        patient.id,
        "ALLOWED",
        changed.isEmpty() ? "no-changes" : String.join(",", changed));
    return PatientView.of(patient);
  }

  /** В аудит — факт и id согласия, без текста основания. */
  @Transactional
  PatientView grantConsent(UUID id, ConsentRequest request) {
    requireRole("ADMINISTRATOR");
    var patient = find(id);
    var consent = patient.grantConsent(request.version(), request.basis());
    patients.saveAndFlush(patient);
    audit.record(
        actorId(), "PATIENT_CONSENT_CHANGED", "CONSENT", consent.id, "ALLOWED", "granted");
    return PatientView.of(patient);
  }

  @Transactional
  PatientView revokeConsent(UUID id, UUID consentId) {
    requireRole("ADMINISTRATOR");
    var patient = find(id);
    var consent = patient.revokeConsent(consentId);
    patients.saveAndFlush(patient);
    audit.record(
        actorId(), "PATIENT_CONSENT_CHANGED", "CONSENT", consent.id, "ALLOWED", "revoked");
    return PatientView.of(patient);
  }

  private Patient find(UUID id) {
    return patients.findById(id).orElseThrow(() -> notFound("Пациент не найден"));
  }
}
