package ru.quietclinic.staff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * CSRF для SPA: токен лежит в cookie {@code XSRF-TOKEN}, фронтенд читает его и отправляет в
 * заголовке {@code X-XSRF-TOKEN} на изменяющих запросах. Схема из документации Spring Security.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
  private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    xor.handle(request, response, csrfToken);
    // Загружаем отложенный токен, чтобы cookie появилась уже на первом ответе.
    csrfToken.get();
  }

  @Override
  public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    var header = request.getHeader(csrfToken.getHeaderName());
    return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
  }
}
