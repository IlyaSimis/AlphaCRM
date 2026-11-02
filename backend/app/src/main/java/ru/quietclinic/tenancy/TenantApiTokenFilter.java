package ru.quietclinic.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Отдельный control-plane token; клинические cookie здесь не принимаются. */
class TenantApiTokenFilter extends OncePerRequestFilter {
  static final String HEADER = "X-Tenancy-Token";
  private final String token;

  TenantApiTokenFilter(String token) {
    this.token = token == null ? "" : token.trim();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String presented = request.getHeader(HEADER);
    if (token.isEmpty()
        || presented == null
        || !MessageDigest.isEqual(
            token.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8))) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      response
          .getWriter()
          .write(
              "{\"code\":\"TENANCY_TOKEN_INVALID\",\"message\":\"Control-plane token is required\"}");
      return;
    }
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                "control-plane", null, List.of(new SimpleGrantedAuthority("ROLE_TENANCY"))));
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
