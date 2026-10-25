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

  /** Клиника, где заведена карточка. Пациенты других клиник не видны. */
  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String fullName;

  @Column(nullable = false)
  LocalDate birthDate;

  @Column(nullable = false)
  Instant createdAt;

  /** Откуда пришёл пациент — значение из {@link ReferralSources}. */
  String referralSource;

  /** Заметка регистратуры: административная, в клинический контур не попадает. */
  String registryNote;

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Contact> contacts = new ArrayList<>();

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Consent> consents = new ArrayList<>();

  static final String TELEGRAM = "TELEGRAM";

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
            .orElseThrow(() -> notFound("CONSENT_NOT_FOUND", "Consent not found"));
    if (consent.revokedAt != null) throw conflict("ALREADY_REVOKED", "Consent is already revoked");
    consent.revokedAt = Instant.now();
    return consent;
  }

  boolean hasActiveConsent() {
    return consents.stream().anyMatch(c -> c.revokedAt == null);
  }

  /** Возвращает имена изменённых полей: в аудит попадает факт изменения, без текста заметки. */
  List<String> updateRegistry(String newReferralSource, String newRegistryNote) {
    var changed = new ArrayList<String>();
    var source = blankToNull(newReferralSource);
    if (!java.util.Objects.equals(source, referralSource)) {
      referralSource = source;
      changed.add("referralSource");
    }
    var note = blankToNull(newRegistryNote);
    if (!java.util.Objects.equals(note, registryNote)) {
      registryNote = note;
      changed.add("registryNote");
    }
    return changed;
  }

  /**
   * Chat id Telegram пациента — контакт TELEGRAM, один на пациента. Пациент получает его, только
   * начав диалог с ботом клиники. {@code remove} — отключить; пустое значение — не менять.
   */
  boolean updateTelegram(String chatId, boolean remove) {
    var current = contacts.stream().filter(c -> TELEGRAM.equals(c.type)).findFirst();
    if (remove) {
      if (current.isEmpty()) return false;
      contacts.remove(current.get());
      return true;
    }
    var value = blankToNull(chatId);
    if (value == null || current.map(c -> c.value.equals(value)).orElse(false)) return false;
    if (current.isPresent()) current.get().value = value;
    else contacts.add(new Contact(this, TELEGRAM, value));
    return true;
  }

  boolean telegramConnected() {
    return contacts.stream().anyMatch(c -> TELEGRAM.equals(c.type));
  }

  String phone() {
    return contacts.stream()
        .filter(c -> "PHONE".equals(c.type))
        .map(c -> c.value)
        .findFirst()
        .orElse(null);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
