package ru.quietclinic.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Отдельная цепочка для API бота: раньше цепочки сотрудников и только для /api/v1/bot/**. Сессия
 * сотрудника здесь не действует, а токен бота не действует на остальных маршрутах.
 */
@Configuration
class BotSecurityConfig {
  @Bean
  @Order(1)
  SecurityFilterChain botSecurity(
      HttpSecurity http, BotClinicResolver clinics, @Value("${clinic.bot.api-token:}") String token)
      throws Exception {
    return http.securityMatcher("/api/v1/bot/**", "/api/v1/messenger/**")
        .csrf(AbstractHttpConfigurer::disable)
        .cors(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .addFilterBefore(new BotTokenFilter(token, clinics), AnonymousAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("BOT"))
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            BotTokenFilter.error(
                                response, 401, "BOT_TOKEN_INVALID", "Bot token is required"))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            BotTokenFilter.error(
                                response, 403, "ACCESS_DENIED", "Not allowed for this role")))
        .build();
  }
}
