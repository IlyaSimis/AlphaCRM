package ru.quietclinic.patients;

import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "patient")
class Patient {
  @Id UUID id;

  @Column(nullable = false)
  String fullName;

  @Column(nullable = false)
  LocalDate birthDate;

  @Column(nullable = false)
  Instant createdAt;

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Contact> contacts = new ArrayList<>();

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Consent> consents = new ArrayList<>();

  protected Patient() {}

  Patient(String fullName, LocalDate birthDate) {
    id = UUID.randomUUID();
    this.fullName = fullName;
    this.birthDate = birthDate;
    createdAt = Instant.now();
  }

  /**
   * Применяет новые ФИО, дату рождения и телефон. Возвращает имена изменённых полей — их, без
   * значений, пишем в аудит.
   */
  List<String> update(String newFullName, LocalDate newBirthDate, String newPhone) {
    var changed = new ArrayList<String>();
    var name = newFullName.trim();
    if (!name.equals(fullName)) {
      fullName = name;
      changed.add("fullName");
    }
    if (!newBirthDate.equals(birthDate)) {
      birthDate = newBirthDate;
      changed.add("birthDate");
    }
    var phone = newPhone.trim();
    var current = contacts.stream().filter(c -> "PHONE".equals(c.type)).findFirst();
    if (current.isEmpty()) {
      contacts.add(new Contact(this, "PHONE", phone));
      changed.add("phone");
    } else if (!phone.equals(current.get().value)) {
      current.get().value = phone;
      changed.add("phone");
    }
    return changed;
  }

  /** Новая действующая версия согласия. Прежние версии не удаляются. */
  Consent grantConsent(String version, String basis) {
    var consent = new Consent(this, version.trim(), basis.trim());
    consents.add(consent);
    return consent;
  }

  Consent revokeConsent(UUID consentId) {
    var consent =
        consents.stream()
            .filter(c -> c.id.equals(consentId))
            .findFirst()
            .orElseThrow(() -> notFound("Согласие не найдено"));
    if (consent.revokedAt != null) throw conflict("ALREADY_REVOKED", "Согласие уже отозвано");
    consent.revokedAt = Instant.now();
    return consent;
  }

  boolean hasActiveConsent() {
    return consents.stream().anyMatch(c -> c.revokedAt == null);
  }
}
