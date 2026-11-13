package ru.quietclinic.clinical;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Экстренный доступ владельца к клинической истории одного пациента (K1). Только чтение, на {@link
 * #TERM}; продление — новый доступ с новой причиной. Текст причины зашифрован.
 */
@Entity
@Table(name = "break_glass_grant")
class BreakGlassGrant {
  static final Duration TERM = Duration.ofMinutes(60);

  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  UUID patientId;

  @Column(nullable = false)
  UUID ownerId;

  @Column(nullable = false)
  String reasonCode;

  @Column(nullable = false)
  String reasonTextEncrypted;

  @Column(nullable = false)
  Instant grantedAt;

  @Column(nullable = false)
  Instant expiresAt;

  protected BreakGlassGrant() {}

  BreakGlassGrant(
      UUID clinicId, UUID patientId, UUID ownerId, String reasonCode, String reasonTextEncrypted) {
    id = UUID.randomUUID();
    this.clinicId = clinicId;
    this.patientId = patientId;
    this.ownerId = ownerId;
    this.reasonCode = reasonCode;
    this.reasonTextEncrypted = reasonTextEncrypted;
    grantedAt = Instant.now();
    expiresAt = grantedAt.plus(TERM);
  }

  boolean activeAt(Instant now) {
    return !grantedAt.isAfter(now) && expiresAt.isAfter(now);
  }
}
