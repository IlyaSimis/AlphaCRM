package ru.quietclinic.bot;

import java.time.OffsetDateTime;

/** Свободное окно в часовом поясе клиники: бот показывает время как есть. */
record BotSlotView(OffsetDateTime startsAt, OffsetDateTime endsAt) {}
