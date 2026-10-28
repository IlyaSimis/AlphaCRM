package ru.quietclinic.patients;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record PatientView(
    UUID id,
    String fullName,
    LocalDate birthDate,
    List<ContactView> contacts,
    List<ConsentView> consents,
    String referralSource,
    String registryNote,
    boolean telegramConnected,
    List<PatientRef> phoneSharedWith,
    String email,
    String secondPhone,
    AssignedStaffView assignedStaff) {

  /** Только ФИО — для кассира: ни даты рождения, ни контактов, ни согласий. */
  PatientView nameOnly() {
    return new PatientView(
        id, fullName, null, List.of(), List.of(), null, null, false, List.of(), null, null, null);
  }

  static PatientView of(
      Patient patient, List<PatientRef> phoneSharedWith, AssignedStaffView assignedStaff) {
    return new PatientView(
        patient.id,
        patient.fullName,
        patient.birthDate,
        // Chat id Telegram на экран не отдаётся: только признак, что контакт есть.
        patient.contacts.stream()
            .filter(c -> !Patient.TELEGRAM.equals(c.type))
            .map(c -> new ContactView(c.type, c.value))
            .toList(),
        patient.consents.stream()
            .map(c -> new ConsentView(c.id, c.version, c.basis, c.grantedAt, c.revokedAt))
            .toList(),
        patient.referralSource,
        patient.registryNote,
        patient.telegramConnected(),
        phoneSharedWith,
        patient.email(),
        patient.secondPhone(),
        assignedStaff);
  }
}
