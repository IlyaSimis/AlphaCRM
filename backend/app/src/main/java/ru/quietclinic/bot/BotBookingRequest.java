package ru.quietclinic.bot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Запись из бота: кто пишет (chat id), данные пациента, врач, начало окна и версия согласия на
 * обработку данных, которое пациент дал, нажав «Подтвердить запись».
 */
record BotBookingRequest(
    @NotBlank @Pattern(regexp = BotChatRequest.CHAT_ID) String telegramChatId,
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
