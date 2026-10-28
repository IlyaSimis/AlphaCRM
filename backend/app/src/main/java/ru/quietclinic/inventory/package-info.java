/**
 * Склад (docs/target-architecture.md, разделы E и G): номенклатура и категории, склады, накладные
 * (приход, расход, списание, перемещение, возврат), остатки, марки (SGTIN) и операции маркировки
 * через {@link ru.quietclinic.inventory.MarkingPort}. Таблицы — в схеме {@code inventory};
 * пациентов модуль не знает: расход по приёму хранит только appointment_id.
 */
package ru.quietclinic.inventory;
