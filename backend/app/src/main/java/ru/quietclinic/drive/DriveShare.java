package ru.quietclinic.drive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Выданное право: сотруднику (USER, id учётной записи), роли (ROLE, код роли), всей клинике
 * (CLINIC) или по ссылке (LINK — только после входа, с обязательным сроком; токен хранится хешем).
 */
@Entity
@Table(name = "drive_share", schema = "drive")
class DriveShare {
  @Id UUID id;

  @Column(nullable = false)
  UUID nodeId;

  @Column(nullable = false)
  String principalType;

  String principalId;

  @Column(nullable = false)
  String permission;

  Instant expiresAt;

  String linkTokenHash;

  @Column(nullable = false)
  UUID createdBy;

  @Column(nullable = false)
  Instant createdAt;

  protected DriveShare() {}

  DriveShare(
      UUID nodeId,
      String principalType,
      String principalId,
      String permission,
      Instant expiresAt,
      String linkTokenHash,
      UUID createdBy) {
    id = UUID.randomUUID();
    this.nodeId = nodeId;
    this.principalType = principalType;
    this.principalId = principalId;
    this.permission = permission;
    this.expiresAt = expiresAt;
    this.linkTokenHash = linkTokenHash;
    this.createdBy = createdBy;
    createdAt = Instant.now();
  }

  boolean active(Instant now) {
    return expiresAt == null || expiresAt.isAfter(now);
  }
}
