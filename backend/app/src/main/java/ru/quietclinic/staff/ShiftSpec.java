package ru.quietclinic.staff;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/** Смена новой карточки врача: ISO-день недели (понедельник = 1), время в шкале расписания. */
public record ShiftSpec(
    @Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}
