package ru.quietclinic.billing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Webhook терминала не принимает сессию сотрудника: только HMAC сырого тела. */
final class TerminalSignatureFilter extends OncePerRequestFilter {
  private static final int MAX_BODY = 16385;
  private final String secret;

  TerminalSignatureFilter(String secret) {
    this.secret = secret == null ? "" : secret.trim();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (secret.length() < 32) {
      response.sendError(401);
      return;
    }
    var body = request.getInputStream().readNBytes(MAX_BODY);
    var wrapped = new CachedBodyRequestWrapper(request, body);
    if (body.length == MAX_BODY
        || !TerminalSignature.valid(secret, body, request.getHeader("X-Terminal-Signature"))) {
      response.sendError(401);
      return;
    }
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            "terminal-webhook", null, List.of(new SimpleGrantedAuthority("ROLE_TERMINAL"))));
    SecurityContextHolder.setContext(context);
    try {
      chain.doFilter(wrapped, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
