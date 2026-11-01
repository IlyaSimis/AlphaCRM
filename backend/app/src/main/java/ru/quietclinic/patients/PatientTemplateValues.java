package ru.quietclinic.patients;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/** Белый список данных пациента, доступных DOCX-шаблонам договоров. */
@Service
public class PatientTemplateValues {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private final PatientRepository patients;
  private final PatientPrivacyService privacy;

  public PatientTemplateValues(PatientRepository patients, PatientPrivacyService privacy) {
    this.patients = patients;
    this.privacy = privacy;
  }

  public Map<String, String> forPatient(UUID patientId, UUID clinicId) {
    Patient patient = patients.findByIdAndClinicId(patientId, clinicId).orElse(null);
    if (patient == null) return null;
    privacy.decrypt(patient);
    Map<String, String> values = new HashMap<>();
    values.put("patient.fullName", patient.fullName);
    values.put("patient.birthDate", DATE.format(patient.birthDate));
    values.put("patient.phone", text(patient.phone()));
    values.put("patient.secondPhone", text(patient.secondPhone()));
    values.put("patient.email", text(patient.email()));
    PatientProfile profile = patient.profile;
    if (profile == null) return values;
    values.put("patient.snils", text(profile.snils()));
    values.put("patient.inn", text(profile.inn()));
    values.put("patient.documentType", text(profile.documentType()));
    values.put("patient.documentSeriesNumber", text(profile.documentSeriesNumber()));
    values.put("patient.documentDepartmentCode", text(profile.documentDepartmentCode()));
    values.put("patient.documentIssuedBy", text(profile.documentIssuedBy()));
    values.put("patient.employer", text(profile.employer()));
    values.put("patient.address", address(profile));
    if (profile.documentIssueDate() != null) {
      values.put("patient.documentIssueDate", DATE.format(profile.documentIssueDate()));
    }
    if (profile.representatives() != null) {
      profile.representatives().stream()
          .filter(item -> Boolean.TRUE.equals(item.primary()))
          .findFirst()
          .or(() -> profile.representatives().stream().findFirst())
          .ifPresent(
              item -> {
                values.put("patient.representativeFullName", text(item.fullName()));
                values.put("patient.representativeRelationship", text(item.relationship()));
              });
    }
    return values;
  }

  private static String address(PatientProfile profile) {
    return Stream.of(
            profile.region(),
            profile.locality(),
            profile.street(),
            profile.house(),
            profile.apartment())
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .reduce((left, right) -> left + ", " + right)
        .orElse("");
  }

  private static String text(String value) {
    return value == null ? "" : value;
  }
}
