package ru.quietclinic.staff;

import jakarta.validation.constraints.NotBlank;

record LoginRequest(@NotBlank String username, @NotBlank String password) {
  /** Пароль не попадает в логи даже при печати запроса. */
  @Override
  public String toString() {
    return "LoginRequest[username=" + username + ", password=***]";
  }
}
