package ru.quietclinic.patients;

import java.util.Objects;
import org.springframework.stereotype.Service;
import ru.quietclinic.platform.EncryptionService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Шифрование административной карточки и контактов с привязкой к клинике, пациенту и полю. */
@Service
class PatientPrivacyService {
  private final EncryptionService encryption;
  private final ObjectMapper json;

  PatientPrivacyService(EncryptionService encryption, ObjectMapper json) {
    this.encryption = encryption;
    this.json = json;
  }

  void decrypt(Patient patient) {
    if (patient.profile == null && patient.profileEncrypted != null) {
      patient.profile = readProfile(patient);
    }
    for (Contact contact : patient.contacts) decrypt(contact);
  }

  void decrypt(Contact contact) {
    if (contact.value != null || contact.encryptedValue == null) return;
    try {
      contact.value = encryption.decrypt(contact.encryptedValue, contactAad(contact));
    } catch (IllegalStateException legacyPlaintext) {
      // Повреждённый или перенесённый шифртекст не должен молча превращаться в «легаси».
      if (contact.encryptedValue.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]{20,}")) {
        throw legacyPlaintext;
      }
      // До миграции 022 здесь был открытый контакт. Он будет зашифрован при ближайшем сохранении.
      contact.value = contact.encryptedValue;
    }
  }

  void encryptLegacy(Patient patient) {
    boolean legacyProfile =
        patient.profileEncrypted != null
            && (patient.profileEncrypted.stripLeading().startsWith("{")
                || patient.profileEncrypted.strip().equals("null"));
    boolean legacyContact =
        patient.contacts.stream()
            .anyMatch(contact -> Objects.equals(contact.value, contact.encryptedValue));
    if (legacyProfile || legacyContact) encrypt(patient);
  }

  void encrypt(Patient patient) {
    if (patient.profile == null) {
      patient.profileEncrypted = null;
    } else {
      try {
        patient.profileEncrypted =
            encryption.encrypt(json.writeValueAsString(patient.profile), profileAad(patient));
      } catch (JacksonException ex) {
        throw new IllegalStateException("Cannot serialize patient profile", ex);
      }
    }
    for (Contact contact : patient.contacts) {
      if (contact.value != null) {
        contact.encryptedValue = encryption.encrypt(contact.value, contactAad(contact));
      }
    }
  }

  private PatientProfile readProfile(Patient patient) {
    try {
      // Старый JSONB после SQL-миграции становится текстом; новый формат начинается с версии ключа.
      if (patient.profileEncrypted.stripLeading().startsWith("{")) {
        return json.readValue(patient.profileEncrypted, PatientProfile.class);
      }
      if (patient.profileEncrypted.strip().equals("null")) return null;
      return json.readValue(
          encryption.decrypt(patient.profileEncrypted, profileAad(patient)), PatientProfile.class);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Cannot read patient profile", ex);
    }
  }

  private static String profileAad(Patient patient) {
    return "patient-profile:" + patient.clinicId + ":" + patient.id;
  }

  private static String contactAad(Contact contact) {
    return "patient-contact:"
        + contact.patient.clinicId
        + ":"
        + contact.patient.id
        + ":"
        + contact.id
        + ":"
        + contact.type;
  }
}
