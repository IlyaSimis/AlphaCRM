package ru.quietclinic.bot;

import java.time.LocalDate;
import java.util.UUID;

/** Пациент для бота: ФИО, дата рождения и основной телефон. */
record BotPatientView(UUID id, String fullName, LocalDate birthDate, String phone) {}
