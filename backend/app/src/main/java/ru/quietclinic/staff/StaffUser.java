package ru.quietclinic.staff;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import ru.quietclinic.platform.RolePermissions;

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

  /** Основная роль: у врача — DOCTOR (с карточкой врача), иначе старшая из ролей. */
  @Column(nullable = false)
  String role;

  /** Все роли сотрудника в клинике; права складываются (раздел C). */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "staff_role", joinColumns = @JoinColumn(name = "staff_id"))
  @Column(name = "role", nullable = false)
  Set<String> roles = new HashSet<>();

  UUID practitionerId;

  @Column(nullable = false)
  boolean active;

  /** Временный пароль от администратора: до смены пароля остальные страницы закрыты. */
  @Column(nullable = false)
  boolean mustChangePassword;

  /** Id текущей HttpSession. Новый вход заменяет значение и вытесняет прежнюю сессию. */
  @Column(length = 36)
  String currentSessionId;

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
    roles.add(role);
    this.practitionerId = practitionerId;
    this.mustChangePassword = mustChangePassword;
    active = true;
    createdAt = Instant.now();
  }

  /** Для врача автор действий — сам врач: clinical и care_relationship сравнивают с ним. */
  UUID actorId() {
    return practitionerId != null ? practitionerId : id;
  }

  /** Роли сотрудника; у записи без строк ролей (старые данные) — основная. */
  Set<String> roleSet() {
    return roles.isEmpty() ? Set.of(role) : Set.copyOf(roles);
  }

  /** Роли в порядке экрана «Сотрудники». */
  java.util.List<String> orderedRoles() {
    var set = roleSet();
    return RolePermissions.STAFF_ROLES.stream().filter(set::contains).toList();
  }

  /** Новый набор ролей: основная — DOCTOR у врача, иначе первая по старшинству. */
  void assignRoles(Set<String> next) {
    roles.clear();
    roles.addAll(next);
    role =
        next.contains(RolePermissions.DOCTOR)
            ? RolePermissions.DOCTOR
            : RolePermissions.STAFF_ROLES.stream().filter(next::contains).findFirst().orElseThrow();
  }
}
