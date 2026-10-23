package ru.quietclinic.staff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;

/** Ответы 401 и 403 в том же формате, что и остальные ошибки API. */
final class JsonErrors {
  private JsonErrors() {}

  static void unauthorized(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    write(response, 401, "UNAUTHORIZED", "Sign-in required");
  }

  static void forbidden(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    if (ex instanceof CsrfException) {
      write(response, 403, "CSRF_INVALID", "CSRF token is missing or invalid");
    } else {
      write(response, 403, "ACCESS_DENIED", "Not allowed for this role");
    }
  }

  static void passwordChangeRequired(HttpServletResponse response) throws IOException {
    write(response, 403, "PASSWORD_CHANGE_REQUIRED", "Change the temporary password first");
  }

  private static void write(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(
            "{\"timestamp\":\""
                + Instant.now()
                + "\",\"status\":"
                + status
                + ",\"code\":\""
                + code
                + "\",\"message\":\""
                + message
                + "\",\"fields\":{}}");
  }
}
