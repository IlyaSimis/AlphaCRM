package ru.quietclinic.telephony;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Отдельная stateless-аутентификация webhook Mango по его ключу и подписи тела JSON. */
final class MangoSignatureFilter extends OncePerRequestFilter {
  private final String key;
  private final String salt;
  private final MangoClinicResolver clinics;

  MangoSignatureFilter(String key, String salt, MangoClinicResolver clinics) {
    this.key = key == null ? "" : key.trim();
    this.salt = salt == null ? "" : salt.trim();
    this.clinics = clinics;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var json = request.getParameter("json");
    if (key.isEmpty() || salt.isEmpty()) {
      error(response, 401, "TELEPHONY_NOT_CONFIGURED", "Mango integration is disabled");
      return;
    }
    if (json == null
        || json.length() > 65536
        || !key.equals(request.getParameter("vpbx_api_key"))
        || !MangoSignature.valid(key, json, salt, request.getParameter("sign"))) {
      error(response, 401, "MANGO_SIGNATURE_INVALID", "Mango signature is missing or wrong");
      return;
    }
    var clinicId = clinics.clinicId();
    if (clinicId == null) {
      error(response, 409, "MANGO_CLINIC_UNKNOWN", "Set MANGO_CLINIC_ID: clinic is ambiguous");
      return;
    }
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new MangoPrincipal(clinicId), null, List.of(new SimpleGrantedAuthority("ROLE_MANGO"))));
    SecurityContextHolder.setContext(context);
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static void error(HttpServletResponse response, int status, String code, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
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
