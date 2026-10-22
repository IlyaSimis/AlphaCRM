package ru.quietclinic.patients;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record PatientView(
    UUID id,
    String fullName,
    LocalDate birthDate,
    List<ContactView> contacts,
    List<ConsentView> consents) {

  static PatientView of(Patient patient) {
    return new PatientView(
        patient.id,
        patient.fullName,
        patient.birthDate,
        patient.contacts.stream().map(c -> new ContactView(c.type, c.value)).toList(),
        patient.consents.stream()
            .map(c -> new ConsentView(c.id, c.version, c.basis, c.grantedAt, c.revokedAt))
            .toList());
  }
}
