package ru.quietclinic.patients;

import java.time.LocalDate;

/**
 * Данные пациента из бота для записи: chat id Telegram, ФИО, дата рождения, телефон и версия
 * согласия, которое пациент дал в боте.
 */
public record TelegramPatientRequest(
    String chatId, String fullName, LocalDate birthDate, String phone, String consentVersion) {}
