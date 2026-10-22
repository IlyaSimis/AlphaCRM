package ru.quietclinic.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class DemoAuthFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var role = request.getHeader("X-Demo-Role");
    var actor = request.getHeader("X-Actor-Id");
    if (role == null) role = "DOCTOR";
    if (actor == null) actor = "00000000-0000-0000-0000-000000000201";
    var correlation = request.getHeader("X-Correlation-Id");
    if (correlation == null) correlation = java.util.UUID.randomUUID().toString();
    MDC.put("correlationId", correlation);
    response.setHeader("X-Correlation-Id", correlation);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
