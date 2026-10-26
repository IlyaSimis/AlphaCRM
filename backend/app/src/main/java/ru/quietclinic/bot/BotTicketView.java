package ru.quietclinic.bot;

import java.util.UUID;

/** Обращение к оператору — id задачи регистратуры и id пациента, если бот его знает. */
record BotTicketView(UUID id, UUID patientId) {}
