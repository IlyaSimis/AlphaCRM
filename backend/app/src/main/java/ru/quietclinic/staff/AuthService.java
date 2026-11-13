package ru.quietclinic.staff;

import static ru.quietclinic.platform.PlatformConfig.conflict;
import static ru.quietclinic.platform.PlatformConfig.tooManyRequests;
import static ru.quietclinic.platform.PlatformConfig.unauthorized;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.quietclinic.audit.AuditService;
import ru.quietclinic.platform.ClinicTime;
import ru.quietclinic.tenancy.TenantDirectory;
import ru.quietclinic.tenancy.TenantScope;

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
  private final TenantDirectory directory;
  private final TransactionTemplate transactions;
  private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

  /** Хеш случайной строки: с ним сверяется пароль неизвестного логина, чтобы уравнять время. */
  private final String unknownUserHash;

  AuthService(
      StaffRepository staff,
      ClinicRepository clinics,
      PasswordEncoder encoder,
      AuditService audit,
      LoginThrottle throttle,
      ClinicTime time,
      TenantDirectory directory,
      PlatformTransactionManager transactionManager) {
    this.staff = staff;
    this.clinics = clinics;
    this.encoder = encoder;
    this.audit = audit;
    this.throttle = throttle;
    this.time = time;
    this.directory = directory;
    transactions = new TransactionTemplate(transactionManager);
    unknownUserHash = encoder.encode(UUID.randomUUID().toString());
  }

  /**
   * До входа tenant известен только управляющему каталогу; транзакция открывается уже в его scope.
   */
  StaffView login(LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
    String username = request.username().trim().toLowerCase(Locale.ROOT);
    UUID tenantId = directory.enabled() ? directory.clinicForLogin(username).orElse(null) : null;
    if (tenantId != null) {
      return TenantScope.call(
          tenantId, () -> loginInTransaction(request, http, response, username));
    }
    return loginInTransaction(request, http, response, username);
  }

  private StaffView loginInTransaction(
      LoginRequest request,
      HttpServletRequest http,
      HttpServletResponse response,
      String username) {
    return transactions.execute(status -> doLogin(request, http, response, username));
  }

  /** Один и тот же ответ на неизвестный логин, неверный пароль и выключенную учётную запись. */
  private StaffView doLogin(
      LoginRequest request,
      HttpServletRequest http,
      HttpServletResponse response,
      String username) {
    String address = http.getRemoteAddr();
    Instant now = time.now();
    // Подбор пароля: после серии неудач вход с этого адреса закрыт на время окна.
    if (throttle.blocked(address, username, now)) {
      throw tooManyRequests("TOO_MANY_LOGIN_ATTEMPTS", "Too many failed sign-in attempts");
    }
    // Блокировка строки не даёт двум одновременным входам оставить две активные сессии.
    Optional<StaffUser> user = staff.findForLogin(username);
    String hash = user.map(u -> u.passwordHash).orElse(unknownUserHash);
    boolean matches = encoder.matches(request.password(), hash);
    if (user.isEmpty() || !matches || !user.get().active) {
      throttle.failed(address, username, now);
      throw unauthorized("INVALID_CREDENTIALS", INVALID_CREDENTIALS);
    }
    throttle.succeeded(address, username);
    StaffPrincipal principal = StaffPrincipal.of(user.get());
    UsernamePasswordAuthenticationToken authentication =
        UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    // Новая сессия на вход: защита от фиксации сессии.
    http.getSession(true);
    http.changeSessionId();
    contexts.saveContext(context, http, response);
    user.get().currentSessionId = http.getSession(false).getId();
    staff.save(user.get());
    return view(user.get());
  }

  /** Поздний выход старой вкладки не отзывает более новую сессию того же сотрудника. */
  @Transactional
  void logout(HttpServletRequest http, Authentication authentication) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
      return;
    }
    HttpSession session = http.getSession(false);
    if (session == null) return;
    staff
        .findById(principal.userId())
        .filter(user -> session.getId().equals(user.currentSessionId))
        .ifPresent(
            user -> {
              user.currentSessionId = null;
              staff.save(user);
            });
  }

  @Transactional(readOnly = true)
  StaffView me() {
    return view(current());
  }

  /** Своя смена пароля снимает требование «сменить временный пароль». */
  @Transactional
  void changeOwnPassword(PasswordChangeRequest request) {
    StaffUser user = current();
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
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
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
