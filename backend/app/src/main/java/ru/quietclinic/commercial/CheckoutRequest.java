package ru.quietclinic.commercial;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CheckoutRequest(@NotBlank @Pattern(regexp = "TEAM|CLINIC") String planCode) {}
