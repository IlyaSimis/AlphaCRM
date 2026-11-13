package ru.quietclinic.inventory;

/** allowNegative — провести расход сверх остатка (только с разрешением, событие StockShortage). */
record PostRequest(Boolean allowNegative) {}
