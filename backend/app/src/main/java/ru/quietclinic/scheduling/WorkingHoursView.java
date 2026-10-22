package ru.quietclinic.scheduling;

record WorkingHoursView(int dayOfWeek, java.time.LocalTime startsAt, java.time.LocalTime endsAt) {}
