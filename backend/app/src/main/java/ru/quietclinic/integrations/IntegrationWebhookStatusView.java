package ru.quietclinic.integrations;

/** Публичный статус подписки без bot token и webhook secret. */
record IntegrationWebhookStatusView(
    String state, String url, String detail, Integer pendingUpdates) {}
