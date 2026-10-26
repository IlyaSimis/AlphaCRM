package ru.quietclinic.scheduling;

import java.time.Instant;

/** Свободное окно врача под услугу онлайн-записи: целиком внутри смены и без пересечений. */
public record FreeSlot(Instant startsAt, Instant endsAt) {}
