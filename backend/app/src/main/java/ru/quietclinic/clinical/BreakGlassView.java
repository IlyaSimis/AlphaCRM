package ru.quietclinic.clinical;

import java.time.Instant;
import java.util.UUID;

/**
 * Экстренный доступ. reasonText отдаётся только в журнале экстренных доступов (владелец и старший
 * администратор); в ответе на выдачу и проверку — без текста.
 */
record BreakGlassView(
    UUID grantId,
    UUID patientId,
    String patientName,
    UUID ownerId,
    String ownerName,
    String reasonCode,
    String reasonText,
    Instant grantedAt,
    Instant expiresAt,
    boolean active) {}
