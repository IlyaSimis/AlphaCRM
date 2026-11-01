package ru.quietclinic.staff;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Закрывает уже открытую сессию сотрудника, которого администратор выключил, и не пускает дальше
 * смены пароля того, кто вошёл с временным паролем. Не бин: добавляется только в цепочку Spring
 * Security, чтобы не сработать дважды.
 */
final class ActiveStaffFilter extends OncePerRequestFilter {
  /** Что доступно до смены временного пароля: кто я, вход, смена пароля, выход и CSRF. */
  static final Set<String> ALLOWED_BEFORE_PASSWORD_CHANGE =
      Set.of(
          "/api/v1/auth/me",
          "/api/v1/auth/login",
          "/api/v1/auth/password",
          "/api/v1/auth/logout",
          "/api/v1/auth/csrf");

  private final StaffRepository staff;

  ActiveStaffFilter(StaffRepository staff) {
    this.staff = staff;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof StaffPrincipal principal) {
      var user = staff.findById(principal.userId()).filter(found -> found.active);
      var session = request.getSession(false);
      var sessionId = session == null ? null : session.getId();
      var currentSessionId = user.map(found -> found.currentSessionId).orElse(null);
      if (user.isEmpty() || currentSessionId == null || !currentSessionId.equals(sessionId)) {
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        // Со старым cookie можно получить CSRF и войти заново, не очищая браузер вручную.
        if (isSessionRecovery(request)) {
          chain.doFilter(request, response);
          return;
        }
        if (currentSessionId != null) {
          JsonErrors.sessionReplaced(response);
        } else {
          JsonErrors.unauthorized(response);
        }
        return;
      }
      if (!user.get().roleSet().equals(principal.roles())) {
        // Роли сменили, пока сотрудник в системе: права пересчитываются с этого же запроса.
        var fresh = StaffPrincipal.of(user.get());
        SecurityContextHolder.getContext()
            .setAuthentication(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                    .authenticated(fresh, null, fresh.authorities()));
      }
      if (user.get().mustChangePassword
          && request.getRequestURI().startsWith("/api/")
          && !ALLOWED_BEFORE_PASSWORD_CHANGE.contains(request.getRequestURI())) {
        JsonErrors.passwordChangeRequired(response);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private boolean isSessionRecovery(HttpServletRequest request) {
    return "/api/v1/auth/login".equals(request.getRequestURI())
        || "/api/v1/auth/csrf".equals(request.getRequestURI());
  }
}
