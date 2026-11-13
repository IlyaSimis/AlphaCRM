package ru.quietclinic.integrations;

/**
 * Публичные настройки Метрики; OAuth-токен хранится отдельно. offlineGoalId пуст — выгрузка
 * выключена.
 */
record YandexMetricaSettings(String apiUrl, String counterId, String offlineGoalId) {}
