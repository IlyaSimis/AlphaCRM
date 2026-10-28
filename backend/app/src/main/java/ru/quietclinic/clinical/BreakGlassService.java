package ru.quietclinic.clinical;

import static ru.quietclinic.platform.PlatformConfig.actorId;
import static ru.quietclinic.platform.PlatformConfig.badRequest;
import static ru.quietclinic.platform.PlatformConfig.can;
import static ru.quietclinic.platform.PlatformConfig.clinicId;
import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.notFound;
import static ru.quietclinic.platform.PlatformConfig.requirePermission;
import static ru.quietclinic.platform.PlatformConfig.staffId;
import static ru.quietclinic.platform.PlatformConfig.tooManyRequests;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.platform.EncryptionService;
import ru.quietclinic.platform.PasswordConfirmation;
import ru.quietclinic.platform.Permission;

/**
 * Экстренный доступ владельца (break-glass, решение K1): только этот пациент, только чтение, 60
 * минут, причина и повторный ввод пароля. В журнал — факт выдачи и каждое чтение с основанием
 * {@code break-glass}; текст причины — только в зашифрованной строке доступа.
 */
@Service
class BreakGlassService {
  /** Неверных паролей подряд, после которых выдача закрывается на {@link #LOCK}. */
  static final int MAX_FAILURES = 5;

  static final Duration LOCK = Duration.ofMinutes(15);

  private final BreakGlassGrantRepository grants;
  private final PasswordConfirmation passwords;
  private final EncryptionService encryption;
  private final AuditService audit;
  private final JdbcTemplate jdbc;
  private final ClinicTime time;
  private final Map<UUID, Deque<Instant>> failures = new ConcurrentHashMap<>();

  BreakGlassService(
      BreakGlassGrantRepository grants,
      PasswordConfirmation passwords,
      EncryptionService encryption,
      AuditService audit,
      JdbcTemplate jdbc,
      ClinicTime time) {
    this.grants = grants;
    this.passwords = passwords;
    this.encryption = encryption;
    this.audit = audit;
    this.jdbc = jdbc;
    this.time = time;
  }

  @Transactional
  BreakGlassView grant(UUID patientId, BreakGlassRequest request) {
    requirePermission(Permission.CLINICAL_BREAK_GLASS);
    requirePatient(patientId);
    var owner = staffId();
    var recent = failures.computeIfAbsent(owner, key -> new ArrayDeque<>());
    synchronized (recent) {
      var since = Instant.now().minus(LOCK);
      while (!recent.isEmpty() && recent.peekFirst().isBefore(since)) recent.pollFirst();
      if (recent.size() >= MAX_FAILURES) {
        throw tooManyRequests(
            "TOO_MANY_ATTEMPTS", "Too many wrong passwords, try again in 15 minutes");
      }
      if (!passwords.confirm(request.password())) {
        recent.addLast(Instant.now());
        audit.record(
            actorId(), "CLINICAL_BREAK_GLASS_GRANTED", "PATIENT", patientId, "DENIED", "password");
        // 409, не 401: сессия жива, неверен только повторно введённый пароль.
        throw conflict("WRONG_PASSWORD", "The password is wrong");
      }
      recent.clear();
    }
    var grant =
        grants.saveAndFlush(
            new BreakGlassGrant(
                clinicId(),
                patientId,
                owner,
                request.reasonCode(),
                encryption.encrypt(request.reasonText().trim())));
    audit.record(
        actorId(),
        "CLINICAL_BREAK_GLASS_GRANTED",
        "PATIENT",
        patientId,
        "ALLOWED",
        request.reasonCode());
    return view(grant, false);
  }

  /** Действующий доступ вошедшего владельца к пациенту; нет — active = false. */
  @Transactional(readOnly = true)
  BreakGlassView current(UUID patientId) {
    requirePermission(Permission.CLINICAL_BREAK_GLASS);
    requirePatient(patientId);
    return grants
        .findFirstByOwnerIdAndPatientIdAndExpiresAtAfterOrderByExpiresAtDesc(
            staffId(), patientId, Instant.now())
        .map(grant -> view(grant, false))
        .orElse(
            new BreakGlassView(null, patientId, null, null, null, null, null, null, null, false));
  }

  /** Есть ли у вошедшего сотрудника действующий экстренный доступ к пациенту. */
  boolean active(UUID patientId) {
    if (!can(Permission.CLINICAL_BREAK_GLASS)) return false;
    var now = Instant.now();
    return grants
        .findFirstByOwnerIdAndPatientIdAndExpiresAtAfterOrderByExpiresAtDesc(
            staffId(), patientId, now)
        .filter(grant -> grant.clinicId.equals(clinicId()) && grant.activeAt(now))
        .isPresent();
  }

  /** Журнал экстренных доступов за период — владелец и старший администратор, с причиной. */
  @Transactional(readOnly = true)
  List<BreakGlassView> list(LocalDate from, LocalDate to) {
    requirePermission(Permission.CLINICAL_BREAK_GLASS_REVIEW);
    if (from == null || to == null || to.isBefore(from)) {
      throw badRequest("INVALID_PERIOD", "The period must have a start before its end");
    }
    var rows =
        grants.findByClinicIdAndGrantedAtBetweenOrderByGrantedAtDesc(
            clinicId(), time.startOf(from), time.startOf(to.plusDays(1)));
    audit.record(
        actorId(), "CLINICAL_BREAK_GLASS_LIST_READ", "CLINIC", clinicId(), "ALLOWED", null);
    return rows.stream().map(grant -> view(grant, true)).toList();
  }

  private void requirePatient(UUID patientId) {
    var count =
        jdbc.queryForObject(
            "select count(*) from patient where id = ? and clinic_id = ?",
            Long.class,
            patientId,
            clinicId());
    if (count == null || count == 0) throw notFound("PATIENT_NOT_FOUND", "Patient not found");
  }

  private BreakGlassView view(BreakGlassGrant grant, boolean withReason) {
    var now = Instant.now();
    return new BreakGlassView(
        grant.id,
        grant.patientId,
        withReason ? name("select full_name from patient where id = ?", grant.patientId) : null,
        grant.ownerId,
        withReason ? name("select full_name from staff_user where id = ?", grant.ownerId) : null,
        grant.reasonCode,
        withReason ? encryption.decrypt(grant.reasonTextEncrypted) : null,
        grant.grantedAt,
        grant.expiresAt,
        grant.activeAt(now));
  }

  private String name(String sql, UUID id) {
    return jdbc.queryForList(sql, String.class, id).stream().findFirst().orElse(null);
  }
}
