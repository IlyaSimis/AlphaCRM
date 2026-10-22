package ru.quietclinic.clinical;

/** Результат старта: существующий приём по той же записи возвращается без создания нового. */
record EncounterStart(ClinicalView view, boolean created) {}
