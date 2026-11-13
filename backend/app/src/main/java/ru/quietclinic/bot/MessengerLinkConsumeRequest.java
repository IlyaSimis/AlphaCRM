package ru.quietclinic.bot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record MessengerLinkConsumeRequest(
    @NotBlank @Pattern(regexp = "^(?i:TELEGRAM|MAX)$") String channel,
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,128}$") String externalId,
    @NotBlank @Size(min = 32, max = 128) String token) {}
