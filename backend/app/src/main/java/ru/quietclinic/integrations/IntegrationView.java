package ru.quietclinic.integrations;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

record IntegrationView(
    UUID id,
    String type,
    String status,
    UUID publicKey,
    JsonNode settings,
    boolean hasSecret,
    Integer secretVersion) {}
