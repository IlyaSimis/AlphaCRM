package ru.quietclinic.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record PasswordResetRequest(@NotBlank @Size(min = 8, max = 128) String newPassword) {
  @Override
  public String toString() {
    return "PasswordResetRequest[***]";
  }
}
