package ru.quietclinic.scheduling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** Режим цены услуги: FIXED с priceMinor или OPEN с пределами minMinor–maxMinor. */
record ServicePricingRequest(
    @NotBlank @Pattern(regexp = "FIXED|OPEN") String priceMode,
    @PositiveOrZero Long priceMinor,
    @PositiveOrZero Long minMinor,
    @PositiveOrZero Long maxMinor) {}
