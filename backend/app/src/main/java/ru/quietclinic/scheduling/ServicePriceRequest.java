package ru.quietclinic.scheduling;

import jakarta.validation.constraints.PositiveOrZero;

record ServicePriceRequest(@PositiveOrZero long priceMinor) {}
