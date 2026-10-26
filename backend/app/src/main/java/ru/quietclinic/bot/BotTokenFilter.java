package ru.quietclinic.bot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Вход бота по сервисному токену в заголовке {@code X-Bot-Token} (CLINIC_BOT_API_TOKEN). Без токена в
 * настройках API бота выключено. Сессия не создаётся, CSRF не нужен: cookie здесь нет. Не бин —
 * добавляется только в цепочку /api/v1/bot/**.
 */
final class BotTokenFilter extends OncePerRequestFilter {
  static final String HEADER = "X-Bot-Token";

  private final byte[] token;
  private final BotClinicResolver clinics;

  BotTokenFilter(String token, BotClinicResolver clinics) {
    this.token = token == null ? new byte[0] : token.trim().getBytes(StandardCharsets.UTF_8);
    this.clinics = clinics;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (token.length == 0) {
      error(response, 401, "BOT_API_DISABLED", "Bot API is disabled: CLINIC_BOT_API_TOKEN is empty");
      return;
    }
    var presented = request.getHeader(HEADER);
    if (presented == null
        || !MessageDigest.isEqual(token, presented.trim().getBytes(StandardCharsets.UTF_8))) {
      error(response, 401, "BOT_TOKEN_INVALID", "Bot token is missing or wrong");
      return;
    }
    var clinicId = clinics.clinicId();
    if (clinicId == null) {
      error(response, 409, "BOT_CLINIC_UNKNOWN", "Set CLINIC_BOT_CLINIC_ID: clinic is ambiguous");
      return;
    }
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new BotPrincipal(clinicId), null, List.of(new SimpleGrantedAuthority("ROLE_BOT"))));
    SecurityContextHolder.setContext(context);
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  static void error(HttpServletResponse response, int status, String code, String message)
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
