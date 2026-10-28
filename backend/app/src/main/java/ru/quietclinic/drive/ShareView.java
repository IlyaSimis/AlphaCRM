package ru.quietclinic.drive;

import java.time.Instant;
import java.util.UUID;

/**
 * Выданное право. label — ФИО сотрудника или код роли; token — только в ответе на создание ссылки
 * (в базе хранится хеш, второй раз токен не показать).
 */
record ShareView(
    UUID id,
    String principalType,
    String principalId,
    String label,
    String permission,
    Instant expiresAt,
    Instant createdAt,
    String token) {}
