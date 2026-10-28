package ru.quietclinic.drive;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** principalId: id учётной записи для USER, код роли для ROLE; для CLINIC и LINK — пусто. */
record ShareRequest(
    @NotBlank @Pattern(regexp = "USER|ROLE|CLINIC|LINK") String principalType,
    @Size(max = 80) String principalId,
    @NotBlank @Pattern(regexp = "VIEW|EDIT|MANAGE") String permission,
    Instant expiresAt) {}
