package ru.quietclinic.staff;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.currentPermissions;
import static ru.quietclinic.platform.PlatformConfig.forbidden;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.Permission;
import ru.quietclinic.platform.RolePermissions;

/**
 * Сотрудники клиники: заводят, выключают и задают временный пароль владелец, старший администратор,
 * администратор и менеджер (разрешение {@code identity.staff.manage}). Роль владельца и старшего
 * администратора назначает только владелец, администратора и техподдержку — администратор и выше:
 * так менеджер не может завести себе администратора. Роль не меняется. Сотрудники других клиник не
 * видны и не меняются.
 */
@Service
class StaffService {
  private final StaffRepository staff;
  private final PasswordEncoder encoder;
  private final AuthService auth;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final ru.quietclinic.platform.DomainEvents events;
  private final PractitionerCards practitionerCards;

  StaffService(
      StaffRepository staff,
      PasswordEncoder encoder,
      AuthService auth,
      JdbcTemplate jdbc,
      AuditService audit,
      PractitionerCards practitionerCards,
      ru.quietclinic.platform.DomainEvents events) {
    this.staff = staff;
    this.encoder = encoder;
    this.auth = auth;
    this.jdbc = jdbc;
    this.audit = audit;
    this.practitionerCards = practitionerCards;
    this.events = events;
  }

  @Transactional(readOnly = true)
  List<StaffView> list() {
    requirePermission(Permission.STAFF_READ);
    return staff.findByClinicIdOrderByFullName(clinicId()).stream().map(auth::view).toList();
  }

  @Transactional
  StaffView create(CreateStaffRequest request) {
    requirePermission(Permission.STAFF_MANAGE);
    var role = request.role().trim().toUpperCase(Locale.ROOT);
    if (!StaffRoles.ALL.contains(role)) {
      throw conflict("INVALID_ROLE", "Unknown staff role");
    }
    requireMayAssign(role);
    var username = request.username().trim().toLowerCase(Locale.ROOT);
    if (staff.existsByUsername(username)) {
      throw conflict("USERNAME_TAKEN", "Username is already taken");
    }
    UUID id = UUID.randomUUID();
    UUID practitionerId = null;
    if (StaffRoles.DOCTOR.equals(role)) {
      var card = request.newPractitioner();
      if (card != null && request.practitionerId() != null) {
        throw conflict(
            "PRACTITIONER_AMBIGUOUS", "Choose an existing practitioner or a new card, not both");
      }
      // Новая карточка создаётся в этой же транзакции: если ниже что-то не так, её не останется.
      practitionerId =
          card != null
              ? practitionerCards.create(
                  request.fullName().trim(), card.specialty().trim(), card.room(), card.shifts())
              : request.practitionerId();
      if (practitionerId == null) {
        throw conflict("PRACTITIONER_REQUIRED", "A doctor account needs a practitioner");
      }
      // Врач — только из своей клиники; чужой выглядит как несуществующий.
      if (!practitionerExists(practitionerId)) {
        throw notFound("PRACTITIONER_NOT_FOUND", "Practitioner not found");
      }
      if (staff.existsByPractitionerId(practitionerId)) {
        throw conflict("PRACTITIONER_TAKEN", "The practitioner already has an account");
      }
      // Как у существующих врачей: id учётной записи совпадает с id врача, если он свободен.
      if (!staff.existsById(practitionerId)) id = practitionerId;
    } else if (request.practitionerId() != null || request.newPractitioner() != null) {
      throw conflict("PRACTITIONER_NOT_ALLOWED", "Only a doctor account can have a practitioner");
    }
    var user =
        staff.save(
            new StaffUser(
                id,
                clinicId(),
                request.fullName().trim(),
                username,
                encoder.encode(request.password()),
                role,
                practitionerId,
                true));
    audit.record(actorId(), "STAFF_CREATED", "STAFF", user.id, "ALLOWED", "role:" + role);
    return auth.view(user);
  }

  @Transactional
  StaffView setActive(UUID id, boolean active) {
    requirePermission(Permission.STAFF_MANAGE);
    var user = find(id);
    requireMayManage(user);
    if (!active && user.id.equals(auth.current().id)) {
      throw conflict("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account");
    }
    user.active = active;
    if (!active) user.currentSessionId = null;
    audit.record(
        actorId(),
        active ? "STAFF_ACTIVATED" : "STAFF_DEACTIVATED",
        "STAFF",
        user.id,
        "ALLOWED",
        null);
    return auth.view(user);
  }

  /** Временный пароль: в базе только хеш, при следующем входе сотрудник обязан его сменить. */
  @Transactional
  void resetPassword(UUID id, PasswordResetRequest request) {
    requirePermission(Permission.STAFF_PASSWORD_RESET, Permission.STAFF_PASSWORD_RESET_PLATFORM);
    var user = find(id);
    var granted = currentPermissions();
    // Техподдержка сбрасывает пароль любому, кроме владельца; остальные — тем, чью роль назначают.
    var allowed =
        (granted.contains(Permission.STAFF_PASSWORD_RESET)
                && user.roleSet().stream()
                    .allMatch(role -> RolePermissions.mayAssign(granted, role)))
            || (granted.contains(Permission.STAFF_PASSWORD_RESET_PLATFORM)
                && !user.roleSet().contains(RolePermissions.OWNER));
    if (!allowed) {
      audit.record(
          actorId(), "STAFF_PASSWORD_RESET", "STAFF", user.id, "DENIED", "role:" + user.role);
      throw forbidden("ROLE_NOT_ASSIGNABLE", "Not allowed for a staff member with this role");
    }
    user.passwordHash = encoder.encode(request.newPassword());
    user.mustChangePassword = true;
    user.currentSessionId = null;
    audit.record(actorId(), "STAFF_PASSWORD_RESET", "STAFF", user.id, "ALLOWED", null);
  }

  /**
   * Новый набор ролей сотрудника (раздел C: одна или несколько ролей, права складываются). Менять
   * можно только роли не выше своих — и прежние, и новые; свои роли — нельзя. Роль врача связана с
   * карточкой врача и здесь не добавляется и не снимается.
   */
  @Transactional
  StaffView setRoles(UUID id, StaffRolesRequest request) {
    requirePermission(Permission.STAFF_MANAGE);
    var user = find(id);
    if (user.id.equals(auth.current().id)) {
      throw conflict("CANNOT_CHANGE_OWN_ROLES", "You cannot change your own roles");
    }
    var next = new java.util.HashSet<String>();
    for (var raw : request.roles()) {
      var role = raw.trim().toUpperCase(Locale.ROOT);
      if (!StaffRoles.ALL.contains(role)) throw conflict("INVALID_ROLE", "Unknown staff role");
      next.add(role);
    }
    if (next.contains(StaffRoles.DOCTOR) != (user.practitionerId != null)) {
      throw conflict(
          "DOCTOR_ROLE_FIXED", "The doctor role follows the practitioner card and cannot change");
    }
    var touched = new java.util.HashSet<>(user.roleSet());
    touched.addAll(next);
    touched.forEach(this::requireMayAssign);
    var before = user.orderedRoles();
    user.assignRoles(next);
    staff.saveAndFlush(user);
    audit.record(
        actorId(),
        "STAFF_ROLES_CHANGED",
        "STAFF",
        user.id,
        "ALLOWED",
        "roles:" + String.join(",", user.orderedRoles()));
    events.publish(
        "RolesChanged",
        java.util.Map.of("staffId", user.id, "before", before, "after", user.orderedRoles()));
    return auth.view(user);
  }

  /** Обслуживать сотрудника можно, только если все его роли не выше своих прав. */
  private void requireMayManage(StaffUser user) {
    user.roleSet().forEach(this::requireMayAssign);
  }

  /** Назначить (и обслуживать) роль можно только не выше своих прав. */
  private void requireMayAssign(String role) {
    if (!RolePermissions.mayAssign(currentPermissions(), role)) {
      throw forbidden("ROLE_NOT_ASSIGNABLE", "Not allowed to manage staff with this role");
    }
  }

  private boolean practitionerExists(UUID practitionerId) {
    var count =
        jdbc.queryForObject(
            "select count(*) from practitioner where id = ? and clinic_id = ?",
            Long.class,
            practitionerId,
            clinicId());
    return count != null && count > 0;
  }

  private StaffUser find(UUID id) {
    return staff
        .findByIdAndClinicId(id, clinicId())
        .orElseThrow(() -> notFound("STAFF_NOT_FOUND", "Staff member not found"));
  }
}
