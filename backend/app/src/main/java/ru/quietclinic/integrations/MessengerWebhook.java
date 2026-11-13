package ru.quietclinic.integrations;

import java.util.UUID;

/** Данные для проверки входящего webhook внутри одной клиники. */
public record MessengerWebhook(UUID clinicId, String secret) {}
