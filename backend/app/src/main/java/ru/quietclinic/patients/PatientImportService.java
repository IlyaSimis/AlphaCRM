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
import java.util.Map;
import java.util.Set;
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
  private final PatientPrivacyService privacy;
  private final ContactTokenService tokens;

  PatientImportService(
      PostgresPatientReader reader,
      PatientRepository patients,
      ContactRepository contacts,
      AuditService audit,
      PatientPrivacyService privacy,
      ContactTokenService tokens) {
    this.reader = reader;
    this.patients = patients;
    this.contacts = contacts;
    this.audit = audit;
    this.privacy = privacy;
    this.tokens = tokens;
  }

  @Transactional(readOnly = true)
  PreviewView preview(ImportRequest request) {
    requirePermission(Permission.PATIENT_WRITE);
    Processed processed = process(request, false);
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
    Processed processed = process(request, true);
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
    List<Patient> existing = patients.findByClinicIdOrderByFullName(clinicId());
    existing.forEach(privacy::decrypt);
    Map<String, Patient> phones = new HashMap<>();
    for (Contact contact :
        contacts.findByTypeInAndPatientClinicId(PhoneIndex.PHONE_TYPES, clinicId())) {
      privacy.decrypt(contact);
      String key = normalizePhone(contact.value);
      if (!key.isEmpty()) phones.putIfAbsent(key, contact.patient);
    }
    Map<String, Patient> identities = new HashMap<>();
    for (Patient patient : existing)
      identities.putIfAbsent(identity(patient.fullName, patient.birthDate), patient);
    Set<String> sourcePhones = new HashSet<>();
    Set<String> sourceIdentities = new HashSet<>();
    List<ImportRowView> rows = new ArrayList<>();
    List<Patient> candidates = new ArrayList<>();
    int duplicates = 0;
    int errors = 0;
    int number = 0;
    int newCount = 0;
    for (ImportedPatient imported : reader.read(request)) {
      number++;
      String fullName = clean(imported.fullName());
      String phone = clean(imported.phone());
      String secondPhone = clean(imported.secondPhone());
      String email = clean(imported.email());
      LocalDate birthDate = null;
      String reason = "";
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
      String phoneKey = normalizePhone(phone);
      String identity = identity(fullName, birthDate);
      Patient duplicate = phones.get(phoneKey);
      if (duplicate == null) duplicate = identities.get(identity);
      String duplicateReason = "DUPLICATE_IN_CRM";
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
        Patient patient = new Patient(fullName, birthDate);
        patient.clinicId = clinicId();
        patient.registryNote = "Импортировано из PostgreSQL";
        patient.update(new PatientDetails(fullName, birthDate, phone, email, secondPhone, null));
        candidates.add(patient);
      }
    }
    if (save) {
      candidates.forEach(privacy::encrypt);
      patients.saveAll(candidates);
      patients.flush();
      candidates.forEach(tokens::rebuild);
    }
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
    String digits = phone.replaceAll("\\D", "");
    if (digits.length() == 11 && digits.startsWith("8")) digits = "7" + digits.substring(1);
    return digits;
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private record Processed(
      int total, int created, int duplicates, int errors, List<ImportRowView> rows) {}
}
