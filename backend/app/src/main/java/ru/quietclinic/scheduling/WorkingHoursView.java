package ru.quietclinic.scheduling;

import java.time.LocalTime;

record WorkingHoursView(int dayOfWeek, LocalTime startsAt, LocalTime endsAt) {}
