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
  private static final String EVENTS_PREFIX = "/api/v1/integrations/mango/events/";

  private final String legacyKey;
  private final String legacySalt;
  private final MangoClinicResolver clinics;
  private final ru.quietclinic.integrations.MangoIntegrationClientFactory integrations;

  MangoSignatureFilter(
      String key,
      String salt,
      MangoClinicResolver clinics,
      ru.quietclinic.integrations.MangoIntegrationClientFactory integrations) {
    this.legacyKey = key == null ? "" : key.trim();
    this.legacySalt = salt == null ? "" : salt.trim();
    this.clinics = clinics;
    this.integrations = integrations;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var json = request.getParameter("json");
    var scoped = scoped(request);
    var publicKey = scoped ? publicKey(request) : null;
    if (scoped && publicKey == null) {
      error(response, 401, "MANGO_SIGNATURE_INVALID", "Mango signature is missing or wrong");
      return;
    }
    String key;
    String salt;
    java.util.UUID clinicId;
    if (scoped) {
      var webhook = integrations.webhook(publicKey).orElse(null);
      if (webhook == null) {
        error(response, 401, "MANGO_SIGNATURE_INVALID", "Mango signature is missing or wrong");
        return;
      }
      key = webhook.apiKey();
      salt = webhook.apiSalt();
      clinicId = webhook.clinicId();
    } else {
      if (legacyKey.isEmpty() || legacySalt.isEmpty()) {
        error(response, 401, "TELEPHONY_NOT_CONFIGURED", "Mango integration is disabled");
        return;
      }
      key = legacyKey;
      salt = legacySalt;
      clinicId = null;
    }
    if (json == null
        || json.length() > 65536
        || !key.equals(request.getParameter("vpbx_api_key"))
        || !MangoSignature.valid(key, json, salt, request.getParameter("sign"))) {
      error(response, 401, "MANGO_SIGNATURE_INVALID", "Mango signature is missing or wrong");
      return;
    }
    if (clinicId == null) {
      clinicId = clinics.clinicId();
      if (clinicId == null) {
        error(response, 409, "MANGO_CLINIC_UNKNOWN", "Set MANGO_CLINIC_ID: clinic is ambiguous");
        return;
      }
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

  private static boolean scoped(HttpServletRequest request) {
    var path = request.getRequestURI().substring(request.getContextPath().length());
    return path.startsWith(EVENTS_PREFIX)
        && path.substring(EVENTS_PREFIX.length()).indexOf('/') >= 0;
  }

  private static java.util.UUID publicKey(HttpServletRequest request) {
    var path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith(EVENTS_PREFIX)) return null;
    var remainder = path.substring(EVENTS_PREFIX.length());
    var slash = remainder.indexOf('/');
    if (slash < 0) return null;
    try {
      return java.util.UUID.fromString(remainder.substring(0, slash));
    } catch (IllegalArgumentException error) {
      return null;
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
