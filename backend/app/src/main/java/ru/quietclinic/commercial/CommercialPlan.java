package ru.quietclinic.commercial;

/** Тариф: лимит относится только к обращениям к внешней модели, локальный поиск бесплатен. */
public record CommercialPlan(
    String code,
    String name,
    String description,
    long priceMinor,
    String currency,
    int monthlyAiRequests) {}
