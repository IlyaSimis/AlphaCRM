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

  /**
   * Постоянный администратор пациента: активный администратор этой клиники. Отключённый остаётся
   * привязанным, пока карточку не поправят.
   */
  UUID assignedStaffId;

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Contact> contacts = new ArrayList<>();

  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  List<Consent> consents = new ArrayList<>();

  static final String TELEGRAM = "TELEGRAM";
  static final String PHONE = "PHONE";
  static final String SECOND_PHONE = "SECOND_PHONE";
  static final String EMAIL = "EMAIL";

  protected Patient() {}

  Patient(String fullName, LocalDate birthDate) {
    id = UUID.randomUUID();
    this.fullName = fullName;
    this.birthDate = birthDate;
    createdAt = Instant.now();
  }

  /**
   * Применяет карточку целиком: ФИО, дату рождения, телефон, e-mail, второй телефон и постоянного
   * администратора. Пустой необязательный контакт удаляется, а не хранится пустой строкой.
   * Возвращает имена изменённых полей — их, без значений, пишем в аудит.
   */
  List<String> update(PatientDetails details) {
    var changed = new ArrayList<String>();
    var name = details.fullName().trim();
    if (!name.equals(fullName)) {
      fullName = name;
      changed.add("fullName");
    }
    if (!details.birthDate().equals(birthDate)) {
      birthDate = details.birthDate();
      changed.add("birthDate");
    }
    if (setContact(PHONE, details.phone())) changed.add("phone");
    if (setContact(EMAIL, details.email())) changed.add("email");
    if (setContact(SECOND_PHONE, details.secondPhone())) changed.add("secondPhone");
    if (!java.util.Objects.equals(details.assignedStaffId(), assignedStaffId)) {
      assignedStaffId = details.assignedStaffId();
      changed.add("assignedStaff");
    }
    return changed;
  }

  /** Один контакт этого типа: новое значение, удаление пустым или «без изменений». */
  private boolean setContact(String type, String newValue) {
    var value = blankToNull(newValue);
    var current = contacts.stream().filter(c -> type.equals(c.type)).findFirst();
    if (value == null) {
      if (current.isEmpty()) return false;
      contacts.remove(current.get());
      return true;
    }
    if (current.isEmpty()) {
      contacts.add(new Contact(this, type, value));
      return true;
    }
    if (value.equals(current.get().value)) return false;
    current.get().value = value;
    return true;
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
    return contact(PHONE);
  }

  String secondPhone() {
    return contact(SECOND_PHONE);
  }

  String email() {
    return contact(EMAIL);
  }

  private String contact(String type) {
    return contacts.stream()
        .filter(c -> type.equals(c.type))
        .map(c -> c.value)
        .findFirst()
        .orElse(null);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
