package ru.quietclinic.patients;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record MessengerLinkRequest(@NotBlank @Pattern(regexp = "^(?i:TELEGRAM|MAX)$") String channel) {}
