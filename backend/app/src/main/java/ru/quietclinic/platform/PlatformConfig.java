package ru.quietclinic.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, DemoAuthFilter demoAuthFilter) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(cors -> {})
        .addFilterBefore(
            demoAuthFilter,
            org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .build();
  }

  @Bean
  org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
    var config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}

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

record ApiError(
    Instant timestamp, int status, String code, String message, Map<String, String> fields) {}

class DomainException extends RuntimeException {
  final String code;
  final HttpStatus status;

  DomainException(String code, String message, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  static DomainException conflict(String code, String message) {
    return new DomainException(code, message, HttpStatus.CONFLICT);
  }

  static DomainException forbidden(String message) {
    return new DomainException("ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
  }
}

@RestControllerAdvice
class ApiExceptionHandler {
  @ExceptionHandler(DomainException.class)
  org.springframework.http.ResponseEntity<ApiError> domain(DomainException ex) {
    return org.springframework.http.ResponseEntity.status(ex.status)
        .body(new ApiError(Instant.now(), ex.status.value(), ex.code, ex.getMessage(), Map.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  org.springframework.http.ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
    var fields = new java.util.LinkedHashMap<String, String>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.put(error.getField(), error.getDefaultMessage()));
    return org.springframework.http.ResponseEntity.badRequest()
        .body(
            new ApiError(Instant.now(), 400, "VALIDATION_ERROR", "Проверьте поля запроса", fields));
  }
}

public final class PlatformConfig {
  private PlatformConfig() {}

  public static void requireRole(String... roles) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    var allowed =
        java.util.Arrays.stream(roles)
            .anyMatch(
                role ->
                    auth != null
                        && auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role)));
    if (!allowed) throw DomainException.forbidden("Недостаточно прав для операции");
  }

  public static java.util.UUID actorId() {
    return java.util.UUID.fromString(
        SecurityContextHolder.getContext().getAuthentication().getName());
  }

  public static RuntimeException conflict(String code, String message) {
    return DomainException.conflict(code, message);
  }

  public static RuntimeException forbidden(String message) {
    return DomainException.forbidden(message);
  }
}
