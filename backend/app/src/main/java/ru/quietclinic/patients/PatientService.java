package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
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
  private final JdbcTemplate jdbc;

  PatientService(
      PatientRepository patients,
      ContactRepository contacts,
      AuditService audit,
      JdbcTemplate jdbc) {
    this.patients = patients;
    this.contacts = contacts;
    this.audit = audit;
    this.jdbc = jdbc;
  }

  /** Карточки, у которых совпадает основной или второй телефон, — предупреждение регистратуре. */
  private PhoneIndex phoneIndex() {
    return new PhoneIndex(
        contacts.findByTypeInAndPatientClinicId(PhoneIndex.PHONE_TYPES, clinicId()));
  }

  /** Сотрудники своей клиники по id — для подписи постоянного администратора. */
  private Map<UUID, AssignedStaffView> clinicStaff() {
    var result = new HashMap<UUID, AssignedStaffView>();
    for (var row :
        jdbc.query(
            "select id, full_name, active from staff_user where clinic_id = ?",
            (rs, i) ->
                new AssignedStaffView(
                    rs.getObject("id", UUID.class),
                    rs.getString("full_name"),
                    rs.getBoolean("active")),
            clinicId())) {
      result.put(row.id(), row);
    }
    return result;
  }

  private PatientView view(Patient patient) {
    return view(patient, phoneIndex(), clinicStaff());
  }

  private static PatientView view(
      Patient patient, PhoneIndex index, Map<UUID, AssignedStaffView> staff) {
    var assigned = patient.assignedStaffId == null ? null : staff.get(patient.assignedStaffId);
    return PatientView.of(patient, index.sharedWith(patient), assigned);
  }

  /**
   * Новый постоянный администратор — активный администратор своей клиники. Сотрудник другой клиники
   * выглядит как несуществующий. Прежняя связь с отключённым сотрудником сохраняется, пока её не
   * поменяют.
   */
  private void checkAssignable(UUID staffId, UUID current) {
    if (staffId == null || staffId.equals(current)) return;
    var rows =
        jdbc.query(
            "select role, active from staff_user where id = ? and clinic_id = ?",
            (rs, i) -> "ADMINISTRATOR".equals(rs.getString("role")) && rs.getBoolean("active"),
            staffId,
            clinicId());
    if (rows.isEmpty()) throw notFound("STAFF_NOT_FOUND", "Staff member not found");
    if (!rows.get(0)) {
      throw conflict(
          "INVALID_ASSIGNED_STAFF", "The assigned staff member must be an active administrator");
    }
  }

  @Transactional(readOnly = true)
  List<PatientView> search(String query) {
    requireRole("ADMINISTRATOR", "DOCTOR", "NURSE", "CASHIER", "MANAGER");
    var index = phoneIndex();
    var staff = clinicStaff();
    var result =
        (query.isBlank()
                ? patients.findByClinicIdOrderByFullName(clinicId())
                : patients.findTop20ByClinicIdAndFullNameContainingIgnoreCaseOrderByFullName(
                    clinicId(), query))
            .stream().map(p -> view(p, index, staff)).toList();
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
    checkAssignable(request.assignedStaffId(), null);
    var patient = new Patient(request.fullName().trim(), request.birthDate());
    patient.clinicId = clinicId();
    patient.update(request.details());
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
    checkAssignable(request.assignedStaffId(), patient.assignedStaffId);
    var changed = patient.update(request.details());
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
    var changed = new ArrayList<>(patient.updateRegistry(source, request.registryNote()));
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
