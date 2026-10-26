package ru.quietclinic.bot;

import java.util.UUID;

/** Врач в боте; room — кабинет или null. */
record BotPractitionerView(UUID id, String fullName, String specialty, String room) {}
