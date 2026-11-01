package ru.quietclinic.staff;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Вход сотрудника по сессии: httpOnly-cookie сессии (SameSite=Lax задан в application.yml), CSRF
 * через cookie {@code XSRF-TOKEN}. Роль и автор действия берутся только из сессии.
 */
@Configuration
class SecurityConfig {
  static final String SESSION_COOKIE = "JSESSIONID";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** Отключает автоконфигурацию пользователя в памяти; вход идёт через {@link AuthService}. */
  @Bean
  UserDetailsService staffUserDetails(StaffRepository staff) {
    return username ->
        staff
            .findByUsername(username)
            .map(
                user ->
                    User.withUsername(user.username)
                        .password(user.passwordHash)
                        .roles(user.role)
                        .disabled(!user.active)
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("not found"));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, StaffRepository staff, AuthService authService)
      throws Exception {
    return http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .cors(Customizer.withDefaults())
        .addFilterAfter(new ActiveStaffFilter(staff), SecurityContextHolderFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(JsonErrors::unauthorized)
                    .accessDeniedHandler(JsonErrors::forbidden))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/v1/auth/logout")
                    .addLogoutHandler(
                        (request, response, authentication) ->
                            authService.logout(request, authentication))
                    .deleteCookies(SESSION_COOKIE, "SESSION")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(204)))
        .requestCache(cache -> cache.disable())
        .build();
  }

  /** Для локальной разработки: явные origin фронтенда и cookie, без «*». */
  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${clinic.cors.allowed-origins}") String allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(
        Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .toList());
    config.setAllowCredentials(true);
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Correlation-Id"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
