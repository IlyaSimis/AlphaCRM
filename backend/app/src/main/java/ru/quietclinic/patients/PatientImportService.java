package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.patients.PatientImportController.ImportRequest;
import ru.quietclinic.patients.PatientImportController.ImportResultView;
import ru.quietclinic.patients.PatientImportController.ImportRowView;
import ru.quietclinic.patients.PatientImportController.PreviewView;
import ru.quietclinic.patients.PostgresPatientReader.ImportedPatient;
import ru.quietclinic.platform.Permission;

/** Повторный запуск не задваивает карточки; существующие данные и согласия не перезаписываются. */
@Service
class PatientImportService {
  private final PostgresPatientReader reader;
  private final PatientRepository patients;
  private final ContactRepository contacts;
  private final AuditService audit;

  PatientImportService(
      PostgresPatientReader reader,
      PatientRepository patients,
      ContactRepository contacts,
      AuditService audit) {
    this.reader = reader;
    this.patients = patients;
    this.contacts = contacts;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  PreviewView preview(ImportRequest request) {
    requirePermission(Permission.PATIENT_WRITE);
    var processed = process(request, false);
    return new PreviewView(
        processed.total(),
        processed.created(),
        processed.duplicates(),
        processed.errors(),
        processed.rows());
  }

  @Transactional
  ImportResultView commit(ImportRequest request) {
    requirePermission(Permission.PATIENT_WRITE);
    var processed = process(request, true);
    audit.record(
        actorId(),
        "PATIENT_IMPORT_COMPLETED",
        "CLINIC",
        clinicId(),
        "ALLOWED",
        "created:"
            + processed.created()
            + ",duplicates:"
            + processed.duplicates()
            + ",errors:"
            + processed.errors());
    return new ImportResultView(
        processed.total(), processed.created(), processed.duplicates(), processed.errors());
  }

  private Processed process(ImportRequest request, boolean save) {
    var existing = patients.findByClinicIdOrderByFullName(clinicId());
    var phones = new HashMap<String, Patient>();
    for (var contact :
        contacts.findByTypeInAndPatientClinicId(PhoneIndex.PHONE_TYPES, clinicId())) {
      var key = normalizePhone(contact.value);
      if (!key.isEmpty()) phones.putIfAbsent(key, contact.patient);
    }
    var identities = new HashMap<String, Patient>();
    for (var patient : existing)
      identities.putIfAbsent(identity(patient.fullName, patient.birthDate), patient);
    var sourcePhones = new HashSet<String>();
    var sourceIdentities = new HashSet<String>();
    var rows = new ArrayList<ImportRowView>();
    var candidates = new ArrayList<Patient>();
    var duplicates = 0;
    var errors = 0;
    var number = 0;
    var newCount = 0;
    for (var imported : reader.read(request)) {
      number++;
      var fullName = clean(imported.fullName());
      var phone = clean(imported.phone());
      var secondPhone = clean(imported.secondPhone());
      var email = clean(imported.email());
      LocalDate birthDate = null;
      var reason = "";
      if (fullName.isEmpty() || fullName.length() > 255) reason = "INVALID_FULL_NAME";
      try {
        birthDate = LocalDate.parse(clean(imported.birthDate()));
        if (!birthDate.isBefore(LocalDate.now())) reason = "INVALID_BIRTH_DATE";
      } catch (DateTimeParseException error) {
        reason = "INVALID_BIRTH_DATE";
      }
      if (phone.isEmpty() || phone.length() > 255) reason = "INVALID_PHONE";
      if (secondPhone.length() > 255 || email.length() > 255) reason = "CONTACT_TOO_LONG";
      if (!email.isEmpty() && !email.matches(PatientRules.EMAIL)) reason = "INVALID_EMAIL";
      if (!reason.isEmpty()) {
        errors++;
        rows.add(row(number, imported, "ERROR", reason, null));
        continue;
      }
      var phoneKey = normalizePhone(phone);
      var identity = identity(fullName, birthDate);
      var duplicate = phones.get(phoneKey);
      if (duplicate == null) duplicate = identities.get(identity);
      var duplicateReason = "DUPLICATE_IN_CRM";
      if (duplicate == null && (!sourcePhones.add(phoneKey) || !sourceIdentities.add(identity))) {
        duplicateReason = "DUPLICATE_IN_SOURCE";
      }
      if (duplicate != null || duplicateReason.equals("DUPLICATE_IN_SOURCE")) {
        duplicates++;
        rows.add(
            row(
                number,
                imported,
                "DUPLICATE",
                duplicateReason,
                duplicate == null ? null : duplicate.fullName));
        continue;
      }
      rows.add(row(number, imported, "NEW", "", null));
      newCount++;
      if (save) {
        var patient = new Patient(fullName, birthDate);
        patient.clinicId = clinicId();
        patient.registryNote = "Импортировано из PostgreSQL";
        patient.update(new PatientDetails(fullName, birthDate, phone, email, secondPhone, null));
        candidates.add(patient);
      }
    }
    if (save) patients.saveAll(candidates);
    return new Processed(number, newCount, duplicates, errors, rows);
  }

  private static ImportRowView row(
      int number, ImportedPatient imported, String status, String reason, String existingName) {
    return new ImportRowView(
        number,
        clean(imported.fullName()),
        clean(imported.birthDate()),
        clean(imported.phone()),
        clean(imported.secondPhone()),
        clean(imported.email()),
        status,
        reason,
        existingName);
  }

  private static String identity(String fullName, LocalDate birthDate) {
    return fullName.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") + "|" + birthDate;
  }

  private static String normalizePhone(String phone) {
    if (phone == null) return "";
    var digits = phone.replaceAll("\\D", "");
    if (digits.length() == 11 && digits.startsWith("8")) digits = "7" + digits.substring(1);
    return digits;
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private record Processed(
      int total, int created, int duplicates, int errors, List<ImportRowView> rows) {}
}
