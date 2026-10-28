package ru.quietclinic.staff;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.quietclinic.platform.PasswordConfirmation;

/** Сверка пароля вошедшего сотрудника — тем же bcrypt, что при входе. */
@Component
class StaffPasswordConfirmation implements PasswordConfirmation {
  private final AuthService auth;
  private final PasswordEncoder encoder;

  StaffPasswordConfirmation(AuthService auth, PasswordEncoder encoder) {
    this.auth = auth;
    this.encoder = encoder;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean confirm(String password) {
    return password != null && encoder.matches(password, auth.current().passwordHash);
  }
}
