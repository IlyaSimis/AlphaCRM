package ru.quietclinic.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Новый сотрудник. Врачу — либо practitionerId уже существующей карточки, либо newPractitioner:
 * карточка создаётся в той же транзакции.
 */
record CreateStaffRequest(
    @NotBlank @Size(max = 255) String fullName,
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") String username,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank String role,
    UUID practitionerId,
    @Valid NewPractitionerRequest newPractitioner) {
  @Override
  public String toString() {
    return "CreateStaffRequest[username=" + username + ", role=" + role + ", password=***]";
  }
}
