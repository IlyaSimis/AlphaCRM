package ru.quietclinic.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
class TerminalSecurityConfig {
  @Bean
  @Order(1)
  SecurityFilterChain terminalSecurity(
      HttpSecurity http, @Value("${clinic.terminal.webhook-secret:}") String secret)
      throws Exception {
    return http.securityMatcher("/api/v1/integrations/terminal/**")
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .addFilterBefore(new TerminalSignatureFilter(secret), AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("TERMINAL"))
        .exceptionHandling(
            errors -> errors.authenticationEntryPoint((req, res, ex) -> res.sendError(401)))
        .build();
  }
}
