package ru.quietclinic.integrations;

import java.util.UUID;

/** Данные для проверки подписанного webhook внутри одной клиники. */
public record MangoWebhook(UUID clinicId, String apiKey, String apiSalt) {}
