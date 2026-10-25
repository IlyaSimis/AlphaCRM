package ru.quietclinic.staff;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requireRole;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;

/**
 * Сотрудники клиники: только администратор этой клиники заводит, выключает и задаёт временный
 * пароль. Роль не меняется. Сотрудники других клиник не видны и не меняются.
 */
@Service
class StaffService {
  private final StaffRepository staff;
  private final PasswordEncoder encoder;
  private final AuthService auth;
  private final JdbcTemplate jdbc;
  private final AuditService audit;
  private final PractitionerCards practitionerCards;

  StaffService(
      StaffRepository staff,
      PasswordEncoder encoder,
      AuthService auth,
      JdbcTemplate jdbc,
      AuditService audit,
      PractitionerCards practitionerCards) {
    this.staff = staff;
    this.encoder = encoder;
    this.auth = auth;
    this.jdbc = jdbc;
    this.audit = audit;
    this.practitionerCards = practitionerCards;
  }

  @Transactional(readOnly = true)
  List<StaffView> list() {
    requireRole("ADMINISTRATOR");
    return staff.findByClinicIdOrderByFullName(clinicId()).stream().map(auth::view).toList();
  }

  @Transactional
  StaffView create(CreateStaffRequest request) {
    requireRole("ADMINISTRATOR");
    var role = request.role().trim().toUpperCase(Locale.ROOT);
    if (!StaffRoles.ALL.contains(role)) {
      throw conflict("INVALID_ROLE", "Unknown staff role");
    }
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
    requireRole("ADMINISTRATOR");
    var user = find(id);
    if (!active && user.id.equals(auth.current().id)) {
      throw conflict("CANNOT_DEACTIVATE_SELF", "You cannot deactivate your own account");
    }
    user.active = active;
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
    requireRole("ADMINISTRATOR");
    var user = find(id);
    user.passwordHash = encoder.encode(request.newPassword());
    user.mustChangePassword = true;
    audit.record(actorId(), "STAFF_PASSWORD_RESET", "STAFF", user.id, "ALLOWED", null);
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
