package ru.quietclinic.integrations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Write-only секреты Mango; этот тип никогда не возвращается из API. */
record IntegrationSecretRequest(
    @NotBlank @Size(max = 512) String apiKey, @NotBlank @Size(max = 512) String apiSalt) {}
