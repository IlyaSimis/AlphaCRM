package ru.quietclinic.inventory;

/** Состояние интеграции маркировки: адаптер и размер очереди. */
record MarkingStatusView(String adapter, long pending, long errors) {}
