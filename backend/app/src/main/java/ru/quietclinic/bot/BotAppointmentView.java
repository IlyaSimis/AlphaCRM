package ru.quietclinic.bot;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Запись для бота. Время — в часовом поясе клиники. status — код записи CRM: BOOKED, CONFIRMED,
 * CANCELLED и остальные.
 */
record BotAppointmentView(
    UUID id,
    UUID practitionerId,
    String practitionerName,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String serviceName,
    String status,
    BotPatientView patient) {}
