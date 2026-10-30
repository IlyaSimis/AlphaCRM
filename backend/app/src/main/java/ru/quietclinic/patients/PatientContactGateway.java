package ru.quietclinic.patients;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Узкая межмодульная точка: выдаёт только запрошенный контакт, не открывая сущности пациента. */
@Service
public class PatientContactGateway {
  private final ContactRepository contacts;
  private final PatientPrivacyService privacy;
  private final ContactTokenService tokens;

  public PatientContactGateway(
      ContactRepository contacts, PatientPrivacyService privacy, ContactTokenService tokens) {
    this.contacts = contacts;
    this.privacy = privacy;
    this.tokens = tokens;
  }

  public Optional<String> value(UUID patientId, UUID clinicId, String type) {
    return contacts.findByPatientIdAndType(patientId, type).stream()
        .filter(contact -> clinicId.equals(contact.patient.clinicId))
        .findFirst()
        .map(
            contact -> {
              privacy.decrypt(contact);
              return contact.value;
            });
  }

  public Optional<String> value(UUID patientId, String type) {
    return contacts.findByPatientIdAndType(patientId, type).stream()
        .findFirst()
        .map(
            contact -> {
              privacy.decrypt(contact);
              return contact.value;
            });
  }

  public List<UUID> exactPhoneOwners(UUID clinicId, String phone) {
    return tokens.exactPhoneMatches(clinicId, phone);
  }
}
