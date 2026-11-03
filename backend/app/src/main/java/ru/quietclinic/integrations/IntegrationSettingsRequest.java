package ru.quietclinic.integrations;

import jakarta.validation.constraints.Size;

record IntegrationSettingsRequest(
    @Size(max = 500) String apiUrl,
    @Size(max = 32) String defaultExtension,
    @Size(max = 32) String lineNumber,
    @Size(max = 500) String publicUrl,
    @Size(max = 500) String relayUrl,
    @Size(max = 500) String webhookBaseUrl,
    Boolean reminderButtons,
    @Size(max = 32) String counterId) {}
