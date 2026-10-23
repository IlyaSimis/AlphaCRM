package ru.quietclinic.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/** Смена в один ISO-день недели (понедельник = 1). */
record ShiftRequest(
    @Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}
