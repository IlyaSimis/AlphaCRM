package ru.quietclinic.bot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Канал и внешний идентификатор личного диалога; никаких данных пациента в адресе нет. */
record MessengerIdentityRequest(
    @NotBlank @Pattern(regexp = "^(?i:TELEGRAM|MAX)$") String channel,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,128}$") String externalId) {}
