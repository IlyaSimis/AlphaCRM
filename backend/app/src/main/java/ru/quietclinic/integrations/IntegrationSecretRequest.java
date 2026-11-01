package ru.quietclinic.integrations;

import jakarta.validation.constraints.Size;

/** Write-only секреты интеграции; этот тип никогда не возвращается из API. */
record IntegrationSecretRequest(
    @Size(max = 512) String apiKey,
    @Size(max = 512) String apiSalt,
    @Size(max = 512) String botToken,
    @Size(max = 512) String relaySecret,
    @Size(max = 256) String webhookSecret) {}
