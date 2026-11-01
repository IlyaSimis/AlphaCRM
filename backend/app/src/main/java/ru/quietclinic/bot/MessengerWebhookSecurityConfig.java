package ru.quietclinic.bot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import ru.quietclinic.integrations.MessengerIntegrationClientFactory;

/** Внешние webhook не принимают ни сессию сотрудника, ни общий CLINIC_BOT_API_TOKEN. */
@Configuration
class MessengerWebhookSecurityConfig {
  @Bean
  @Order(1)
  SecurityFilterChain messengerWebhookSecurity(
      HttpSecurity http, MessengerIntegrationClientFactory integrations) throws Exception {
    return http.securityMatcher(
            "/api/v1/integrations/telegram/events/**", "/api/v1/integrations/max/events/**")
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .addFilterBefore(
            new MessengerWebhookFilter(integrations), AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("BOT"))
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    (request, response, error) ->
                        BotTokenFilter.error(
                            response,
                            401,
                            "MESSENGER_WEBHOOK_UNAUTHORIZED",
                            "Webhook authentication failed")))
        .build();
  }
}
