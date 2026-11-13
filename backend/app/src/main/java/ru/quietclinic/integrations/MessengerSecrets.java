package ru.quietclinic.integrations;

/** Write-only секреты Bot API, необязательного Telegram relay и входящего webhook. */
public record MessengerSecrets(String botToken, String relaySecret, String webhookSecret) {}
