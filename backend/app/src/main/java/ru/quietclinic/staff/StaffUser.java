package ru.quietclinic.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Учётная запись сотрудника клиники. Хеш пароля наружу не отдаётся ни в одном ответе API. */
@Entity
@Table(name = "staff_user")
class StaffUser {
  @Id UUID id;

  @Column(nullable = false)
  UUID clinicId;

  @Column(nullable = false)
  String fullName;

  @Column(nullable = false, unique = true)
  String username;

  @Column(nullable = false)
  String passwordHash;

  @Column(nullable = false)
  String role;

  UUID practitionerId;

  @Column(nullable = false)
  boolean active;

  /** Временный пароль от администратора: до смены пароля остальные страницы закрыты. */
  @Column(nullable = false)
  boolean mustChangePassword;

  @Column(nullable = false)
  Instant createdAt;

  protected StaffUser() {}

  StaffUser(
      UUID id,
      UUID clinicId,
      String fullName,
      String username,
      String passwordHash,
      String role,
      UUID practitionerId,
      boolean mustChangePassword) {
    this.id = id;
    this.clinicId = clinicId;
    this.fullName = fullName;
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = role;
    this.practitionerId = practitionerId;
    this.mustChangePassword = mustChangePassword;
    active = true;
    createdAt = Instant.now();
  }

  /** Для врача автор действий — сам врач: clinical и care_relationship сравнивают с ним. */
  UUID actorId() {
    return practitionerId != null ? practitionerId : id;
  }
}
