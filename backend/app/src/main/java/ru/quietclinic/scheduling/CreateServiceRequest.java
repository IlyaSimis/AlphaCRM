package ru.quietclinic.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Новая услуга: название, длительность в минутах, цена в копейках; валюта пока только RUB. */
record CreateServiceRequest(
    @NotBlank @Size(max = 255) String name,
    @Positive @Max(720) int durationMinutes,
    @PositiveOrZero long priceMinor,
    @Pattern(regexp = "RUB") String currency) {}
