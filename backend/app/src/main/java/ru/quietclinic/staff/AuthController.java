package ru.quietclinic.staff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Выход — {@code POST /api/v1/auth/logout}, его обрабатывает Spring Security. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService auth;

  AuthController(AuthService auth) {
    this.auth = auth;
  }

  /** Выдаёт cookie {@code XSRF-TOKEN} до первого изменяющего запроса (например, до входа). */
  @GetMapping("/csrf")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void csrf() {}

  @PostMapping("/login")
  StaffView login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest http,
      HttpServletResponse response) {
    return auth.login(request, http, response);
  }

  @GetMapping("/me")
  StaffView me() {
    return auth.me();
  }

  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changePassword(@Valid @RequestBody PasswordChangeRequest request) {
    auth.changeOwnPassword(request);
  }
}
