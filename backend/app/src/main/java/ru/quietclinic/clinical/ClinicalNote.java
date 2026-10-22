package ru.quietclinic.clinical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clinical_note")
class ClinicalNote {
  @Id UUID id;

  @Column(nullable = false, unique = true)
  UUID encounterId;

  @Column(nullable = false, length = 16000)
  String complaintsEncrypted;

  @Column(nullable = false, length = 16000)
  String mentalStatusEncrypted;

  @Column(nullable = false, length = 16000)
  String diagnosisEncrypted;

  @Column(nullable = false, length = 16000)
  String recommendationsEncrypted;

  @Column(nullable = false)
  UUID authorId;

  @Column(nullable = false)
  Instant updatedAt;

  @Version long version;

  protected ClinicalNote() {}
}
