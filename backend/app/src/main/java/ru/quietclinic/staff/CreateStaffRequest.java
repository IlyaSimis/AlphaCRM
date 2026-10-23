package ru.quietclinic.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record CreateStaffRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") String username,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank String role,
    UUID practitionerId) {
  @Override
  public String toString() {
    return "CreateStaffRequest[username=" + username + ", role=" + role + ", password=***]";
  }
}
