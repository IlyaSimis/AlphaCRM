package ru.quietclinic.clinical;

import static ru.quietclinic.platform.PlatformConfig.conflict;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;

/**
 * PDF пациента. В базе только шифртекст файла. После подписи (SIGNED_LOCKED) байты, имя, хеш и
 * подписант не меняются: здесь — отказ при любом изменении загруженной подписанной строки, в
 * PostgreSQL — триггер patient_file_immutable. Замена — новый файл; удаления нет.
 */
@Entity
@Table(name = "patient_file")
class PatientFile {
  static final String DRAFT = "DRAFT";
  static final String SIGNED = "SIGNED_LOCKED";

  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  UUID patientId;

  UUID appointmentId;

  @Column(nullable = false)
  String originalName;

  @Column(nullable = false)
  long sizeBytes;

  @Column(nullable = false)
  byte[] ciphertext;

  @Column(nullable = false)
  String state;

  /** SHA-256 открытых байтов файла, считается при подписи. */
  String contentHash;

  @Column(nullable = false)
  UUID uploadedBy;

  @Column(nullable = false)
  Instant uploadedAt;

  UUID signedBy;
  Instant signedAt;

  /** Строка была подписанной уже при чтении из базы — менять её нельзя. */
  @Transient boolean lockedWhenLoaded;

  protected PatientFile() {}

  PatientFile(
      UUID clinicId,
      UUID patientId,
      UUID appointmentId,
      String originalName,
      long sizeBytes,
      byte[] ciphertext,
      UUID uploadedBy,
      Instant uploadedAt) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.patientId = patientId;
    this.appointmentId = appointmentId;
    this.originalName = originalName;
    this.sizeBytes = sizeBytes;
    this.ciphertext = ciphertext;
    this.uploadedBy = uploadedBy;
    this.uploadedAt = uploadedAt;
    state = DRAFT;
  }

  boolean signed() {
    return SIGNED.equals(state);
  }

  /** Подпись фиксирует, кто и когда подтвердил файл. Повторная подпись — конфликт. */
  void sign(UUID signer, String hash, Instant at) {
    if (signed()) throw conflict("ALREADY_SIGNED", "The file is already signed");
    state = SIGNED;
    contentHash = hash;
    signedBy = signer;
    signedAt = at;
  }

  @PostLoad
  void rememberLock() {
    lockedWhenLoaded = signed();
  }

  @PreUpdate
  void refuseChangesAfterSigning() {
    if (lockedWhenLoaded) throw conflict("FILE_LOCKED", "Signed file is immutable");
  }
}
