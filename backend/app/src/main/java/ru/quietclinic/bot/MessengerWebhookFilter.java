package ru.quietclinic.bot;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.quietclinic.integrations.MessengerIntegrationClientFactory;

/** Аутентифицирует scoped webhook и создаёт BOT-контекст именно его клиники. */
final class MessengerWebhookFilter extends OncePerRequestFilter {
  static final String TELEGRAM_HEADER = "X-Telegram-Bot-Api-Secret-Token";
  static final String MAX_HEADER = "X-Max-Bot-Api-Secret";
  private static final String PREFIX = "/api/v1/integrations/";

  private final MessengerIntegrationClientFactory integrations;

  MessengerWebhookFilter(MessengerIntegrationClientFactory integrations) {
    this.integrations = integrations;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var route = route(request);
    if (route == null) {
      BotTokenFilter.error(
          response, 401, "MESSENGER_WEBHOOK_UNAUTHORIZED", "Webhook authentication failed");
      return;
    }
    var webhook = integrations.webhook(route.publicKey(), route.type()).orElse(null);
    var header = "TELEGRAM".equals(route.type()) ? TELEGRAM_HEADER : MAX_HEADER;
    var presented = request.getHeader(header);
    if (webhook == null
        || presented == null
        || !MessageDigest.isEqual(
            webhook.secret().getBytes(StandardCharsets.UTF_8),
            presented.trim().getBytes(StandardCharsets.UTF_8))) {
      BotTokenFilter.error(
          response, 401, "MESSENGER_WEBHOOK_UNAUTHORIZED", "Webhook authentication failed");
      return;
    }
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            new BotPrincipal(webhook.clinicId()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_BOT"))));
    SecurityContextHolder.setContext(context);
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private static WebhookRoute route(HttpServletRequest request) {
    var path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.startsWith(PREFIX)) return null;
    var parts = path.substring(PREFIX.length()).split("/");
    if (parts.length != 3 || !"events".equals(parts[1])) return null;
    var type = parts[0].toUpperCase(java.util.Locale.ROOT);
    if (!List.of("TELEGRAM", "MAX").contains(type)) return null;
    try {
      return new WebhookRoute(UUID.fromString(parts[2]), type);
    } catch (IllegalArgumentException error) {
      return null;
    }
  }

  private record WebhookRoute(UUID publicKey, String type) {}
}
