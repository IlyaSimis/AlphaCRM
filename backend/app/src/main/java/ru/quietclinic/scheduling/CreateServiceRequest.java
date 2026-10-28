package ru.quietclinic.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Новая услуга: название, длительность в минутах и цена в копейках; валюта пока только RUB. Режим
 * цены по умолчанию FIXED; для OPEN вместо цены — пределы minMinor и maxMinor.
 */
record CreateServiceRequest(
    @NotBlank @Size(max = 255) String name,
    @Positive @Max(720) int durationMinutes,
    @PositiveOrZero Long priceMinor,
    @Pattern(regexp = "RUB") String currency,
    @Pattern(regexp = "FIXED|OPEN") String priceMode,
    @PositiveOrZero Long minMinor,
    @PositiveOrZero Long maxMinor) {}
