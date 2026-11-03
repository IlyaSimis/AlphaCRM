package ru.quietclinic.integrations;

/** Публичные настройки подключения к Метрике; OAuth-токен хранится отдельно. */
record YandexMetricaSettings(String apiUrl, String counterId) {}
