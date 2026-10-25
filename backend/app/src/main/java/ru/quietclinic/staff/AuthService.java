package ru.quietclinic.staff;

import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.tooManyRequests;
import static ru.quietclinic.platform.PlatformConfig.unauthorized;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.ClinicTime;

/** Вход, текущий сотрудник и смена собственного пароля. Пароли не логируются. */
@Service
class AuthService {
  static final String INVALID_CREDENTIALS = "Wrong username or password";

  private final StaffRepository staff;
  private final ClinicRepository clinics;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final LoginThrottle throttle;
  private final ClinicTime time;
  private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

  /** Хеш случайной строки: с ним сверяется пароль неизвестного логина, чтобы уравнять время. */
  private final String unknownUserHash;

  AuthService(
      StaffRepository staff,
      ClinicRepository clinics,
      PasswordEncoder encoder,
      AuditService audit,
      LoginThrottle throttle,
      ClinicTime time) {
    this.staff = staff;
    this.clinics = clinics;
    this.encoder = encoder;
    this.audit = audit;
    this.throttle = throttle;
    this.time = time;
    unknownUserHash = encoder.encode(UUID.randomUUID().toString());
  }

  /** Один и тот же ответ на неизвестный логин, неверный пароль и выключенную учётную запись. */
  @Transactional(readOnly = true)
  StaffView login(LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
    var address = http.getRemoteAddr();
    var username = request.username().trim().toLowerCase(Locale.ROOT);
    var now = time.now();
    // Подбор пароля: после серии неудач вход с этого адреса закрыт на время окна.
    if (throttle.blocked(address, username, now)) {
      throw tooManyRequests("TOO_MANY_LOGIN_ATTEMPTS", "Too many failed sign-in attempts");
    }
    var user = staff.findByUsername(username);
    var hash = user.map(u -> u.passwordHash).orElse(unknownUserHash);
    var matches = encoder.matches(request.password(), hash);
    if (user.isEmpty() || !matches || !user.get().active) {
      throttle.failed(address, username, now);
      throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
    }
    throttle.succeeded(address, username);
    var principal = StaffPrincipal.of(user.get());
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    // Новая сессия на вход: защита от фиксации сессии.
    http.getSession(true);
    http.changeSessionId();
    contexts.saveContext(context, http, response);
    return view(user.get());
  }

  @Transactional(readOnly = true)
  StaffView me() {
    return view(current());
  }

  /** Своя смена пароля снимает требование «сменить временный пароль». */
  @Transactional
  void changeOwnPassword(PasswordChangeRequest request) {
    var user = current();
    if (!encoder.matches(request.currentPassword(), user.passwordHash)) {
      throw conflict("WRONG_PASSWORD", "Current password is wrong");
    }
    if (user.mustChangePassword && request.currentPassword().equals(request.newPassword())) {
      throw conflict("PASSWORD_NOT_CHANGED", "New password must differ from the temporary one");
    }
    user.passwordHash = encoder.encode(request.newPassword());
    user.mustChangePassword = false;
    audit.record(user.actorId(), "STAFF_PASSWORD_CHANGED", "STAFF", user.id, "ALLOWED", "self");
  }

  StaffUser current() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof StaffPrincipal principal)) {
      throw unauthorized("Sign-in required");
    }
    return staff
        .findById(principal.userId())
        .filter(user -> user.active)
        .orElseThrow(() -> unauthorized("Sign-in required"));
  }

  StaffView view(StaffUser user) {
    return StaffView.of(
        user,
        clinics.findById(user.clinicId).map(clinic -> clinic.name).orElse(null),
        time.zone().getId());
  }
}
