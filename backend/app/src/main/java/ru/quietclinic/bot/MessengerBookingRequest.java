package ru.quietclinic.bot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Онлайн-запись из любого поддерживаемого мессенджера. */
record MessengerBookingRequest(
    @NotBlank @Pattern(regexp = "^(?i:TELEGRAM|MAX)$") String channel,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,128}$") String externalId,
    @NotBlank @Size(max = 80) String lastName,
    @NotBlank @Size(max = 80) String firstName,
    @Size(max = 80) String patronymic,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Size(max = 40) String phone,
    @NotNull UUID practitionerId,
    @NotNull OffsetDateTime startsAt,
    @NotBlank @Size(max = 80) String consentVersion) {

  String fullName() {
    String name = lastName.trim() + " " + firstName.trim();
    return patronymic == null || patronymic.isBlank() ? name : name + " " + patronymic.trim();
  }
}
