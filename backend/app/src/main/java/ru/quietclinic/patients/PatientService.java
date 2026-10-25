package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;

/**
 * Административные данные пациента: поиск, регистрация, карточка и редактирование. Всё — в пределах
 * клиники сотрудника: карточка другой клиники выглядит как несуществующая.
 */
@Service
class PatientService {
  private final PatientRepository patients;
  private final ContactRepository contacts;
  private final AuditService audit;

  PatientService(PatientRepository patients, ContactRepository contacts, AuditService audit) {
    this.patients = patients;
    this.contacts = contacts;
    this.audit = audit;
  }

  /** Карточки, у которых совпадает телефон, — для предупреждения регистратуре. */
  private PhoneIndex phoneIndex() {
    return new PhoneIndex(contacts.findByTypeAndPatientClinicId("PHONE", clinicId()));
  }

  private PatientView view(Patient patient) {
    return PatientView.of(patient, phoneIndex().sharedWith(patient));
  }

  @Transactional(readOnly = true)
  List<PatientView> search(String query) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var index = phoneIndex();
    var result =
        (query.isBlank()
                ? patients.findByClinicIdOrderByFullName(clinicId())
                : patients.findTop20ByClinicIdAndFullNameContainingIgnoreCaseOrderByFullName(
                    clinicId(), query))
            .stream().map(p -> PatientView.of(p, index.sharedWith(p))).toList();
    // Одно событие на просмотр списка, а не по событию на каждого пациента: иначе журнал из
    // последних 100 событий целиком забивается одним открытием списка.
    audit.record(
        actorId(),
        "PATIENT_LIST_READ",
        "CLINIC",
        clinicId(),
        "ALLOWED",
        (query.isBlank() ? "list:" : "search:") + result.size());
    return result;
  }

  @Transactional
  PatientView create(CreatePatientRequest request) {
    requireRole("ADMINISTRATOR");
    var patient = new Patient(request.fullName(), request.birthDate());
    patient.clinicId = clinicId();
    patient.contacts.add(new Contact(patient, "PHONE", request.phone()));
    patient.consents.add(new Consent(patient, request.consentVersion(), request.consentBasis()));
    patients.save(patient);
    audit.record(actorId(), "PATIENT_CREATED", "PATIENT", patient.id, "ALLOWED", "registration");
    return view(patient);
  }

  @Transactional(readOnly = true)
  PatientView get(UUID id) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var patient = find(id);
    audit.record(actorId(), "PATIENT_ADMIN_READ", "PATIENT", patient.id, "ALLOWED", "card");
    return view(patient);
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
    return view(patient);
  }

  /** В аудит — факт и id согласия, без текста основания. */
  @Transactional
  PatientView grantConsent(UUID id, ConsentRequest request) {
    requireRole("ADMINISTRATOR");
    var patient = find(id);
    var consent = patient.grantConsent(request.version(), request.basis());
    patients.saveAndFlush(patient);
    audit.record(actorId(), "PATIENT_CONSENT_CHANGED", "CONSENT", consent.id, "ALLOWED", "granted");
    return view(patient);
  }

  @Transactional
  PatientView revokeConsent(UUID id, UUID consentId) {
    requireRole("ADMINISTRATOR");
    var patient = find(id);
    var consent = patient.revokeConsent(consentId);
    patients.saveAndFlush(patient);
    audit.record(actorId(), "PATIENT_CONSENT_CHANGED", "CONSENT", consent.id, "ALLOWED", "revoked");
    return view(patient);
  }

  /** Источник и заметка регистратуры. В аудит — имена изменённых полей, без текста заметки. */
  @Transactional
  PatientView updateRegistry(UUID id, RegistryRequest request) {
    requireRole("ADMINISTRATOR");
    var source = request.referralSource();
    if (source != null && !source.isBlank() && !ReferralSources.ALL.contains(source)) {
      throw conflict("INVALID_REFERRAL_SOURCE", "Unknown referral source");
    }
    var patient = find(id);
    var changed =
        new ArrayList<>(patient.updateRegistry(source, request.registryNote()));
    if (patient.updateTelegram(
        request.telegramChatId(), Boolean.TRUE.equals(request.removeTelegram()))) {
      changed.add("telegram");
    }
    patients.saveAndFlush(patient);
    audit.record(
        actorId(),
        "PATIENT_REGISTRY_UPDATED",
        "PATIENT",
        patient.id,
        "ALLOWED",
        changed.isEmpty() ? "no-changes" : String.join(",", changed));
    return view(patient);
  }

  private Patient find(UUID id) {
    return patients
        .findByIdAndClinicId(id, clinicId())
        .orElseThrow(() -> notFound("PATIENT_NOT_FOUND", "Patient not found"));
  }
}
