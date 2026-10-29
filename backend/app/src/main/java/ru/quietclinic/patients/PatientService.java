package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;

/**
 * Административные данные пациента: поиск, регистрация, карточка и редактирование. Всё — в пределах
 * клиники сотрудника: карточка другой клиники выглядит как несуществующая.
 *
 * <p>Доступ по матрице RBAC: врач видит карточки только пациентов, с которыми у него активная связь
 * с лечением ({@code clinical.patient.read-care}); кассир — только ФИО (без {@code
 * clinical.patient.contacts-read}); техподдержка пациентов не видит.
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
    var view = PatientView.of(patient, index.sharedWith(patient), assigned);
    return can(Permission.PATIENT_CONTACTS_READ) ? view : view.nameOnly();
  }

  /** Пациенты с активной связью с лечением у текущего врача. */
  private java.util.Set<UUID> carePatients() {
    return new java.util.HashSet<>(
        jdbc.queryForList(
            "select patient_id from care_relationship where practitioner_id = ?"
                + " and valid_from <= ? and valid_until > ?",
            UUID.class,
            actorId(),
            java.sql.Timestamp.from(java.time.Instant.now()),
            java.sql.Timestamp.from(java.time.Instant.now())));
  }

  /** Все пациенты клиники или только пациенты врача по связи с лечением. */
  private boolean visible(Patient patient, java.util.Set<UUID> care) {
    return can(Permission.PATIENT_READ) || (care != null && care.contains(patient.id));
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
            "select u.active, (select count(*) from staff_role r where r.staff_id = u.id"
                + " and r.role in ('OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR')) as admin"
                + " from staff_user u where u.id = ? and u.clinic_id = ?",
            (rs, i) -> rs.getLong("admin") > 0 && rs.getBoolean("active"),
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
    requirePermission(Permission.PATIENT_READ, Permission.PATIENT_READ_CARE);
    var index = phoneIndex();
    var staff = clinicStaff();
    var care = can(Permission.PATIENT_READ) ? null : carePatients();
    var matches = new java.util.LinkedHashMap<UUID, Patient>();
    if (query.isBlank()) {
      patients.findByClinicIdOrderByFullName(clinicId()).forEach(p -> matches.put(p.id, p));
    } else {
      patients
          .findTop20ByClinicIdAndFullNameContainingIgnoreCaseOrderByFullName(
              clinicId(), query.trim())
          .forEach(p -> matches.put(p.id, p));
      // Телефоны сравниваем по цифрам в уже загруженном индексе клиники: формат и 8/+7 не важны.
      index.matching(query).forEach(p -> matches.putIfAbsent(p.id, p));
    }
    var result =
        matches.values().stream()
            .sorted(
                java.util.Comparator.comparing(
                    (Patient p) -> p.fullName, String.CASE_INSENSITIVE_ORDER))
            .filter(p -> visible(p, care))
            .map(p -> view(p, index, staff))
            .toList();
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
    requirePermission(Permission.PATIENT_WRITE);
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
    requirePermission(Permission.PATIENT_READ, Permission.PATIENT_READ_CARE);
    var patient = find(id);
    if (!visible(patient, can(Permission.PATIENT_READ) ? null : carePatients())) {
      audit.record(actorId(), "PATIENT_ADMIN_READ", "PATIENT", patient.id, "DENIED", "no-care");
      throw notFound("PATIENT_NOT_FOUND", "Patient not found");
    }
    audit.record(actorId(), "PATIENT_ADMIN_READ", "PATIENT", patient.id, "ALLOWED", "card");
    return view(patient);
  }

  @Transactional
  PatientView update(UUID id, UpdatePatientRequest request) {
    requirePermission(Permission.PATIENT_WRITE);
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
    requirePermission(Permission.PATIENT_WRITE);
    var patient = find(id);
    var consent = patient.grantConsent(request.version(), request.basis());
    patients.saveAndFlush(patient);
    audit.record(actorId(), "PATIENT_CONSENT_CHANGED", "CONSENT", consent.id, "ALLOWED", "granted");
    return view(patient);
  }

  @Transactional
  PatientView revokeConsent(UUID id, UUID consentId) {
    requirePermission(Permission.PATIENT_WRITE);
    var patient = find(id);
    var consent = patient.revokeConsent(consentId);
    patients.saveAndFlush(patient);
    audit.record(actorId(), "PATIENT_CONSENT_CHANGED", "CONSENT", consent.id, "ALLOWED", "revoked");
    return view(patient);
  }

  /** Источник и заметка регистратуры. В аудит — имена изменённых полей, без текста заметки. */
  @Transactional
  PatientView updateRegistry(UUID id, RegistryRequest request) {
    requirePermission(Permission.PATIENT_WRITE);
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
