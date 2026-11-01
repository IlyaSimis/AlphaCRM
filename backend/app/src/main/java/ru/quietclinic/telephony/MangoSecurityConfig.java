package ru.quietclinic.telephony;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import ru.quietclinic.integrations.MangoIntegrationClientFactory;

/** Webhook Mango не принимает сессию сотрудника и не работает без действительной подписи. */
@Configuration
class MangoSecurityConfig {
  @Bean
  @Order(1)
  SecurityFilterChain mangoSecurity(
      HttpSecurity http,
      MangoClinicResolver clinics,
      MangoIntegrationClientFactory integrations,
      @Value("${clinic.telephony.mango.api-key:}") String key,
      @Value("${clinic.telephony.mango.api-salt:}") String salt)
      throws Exception {
    return http.securityMatcher("/api/v1/integrations/mango/events/**")
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .addFilterBefore(
            new MangoSignatureFilter(key, salt, clinics, integrations),
            AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("MANGO"))
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    (request, response, error) -> MangoSignatureFilterAccess.error(response)))
        .build();
  }
}

/** Не раскрывает подробности отказа внешнему вызывающему. */
final class MangoSignatureFilterAccess {
  private MangoSignatureFilterAccess() {}

  static void error(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
    response.sendError(401);
  }
}
