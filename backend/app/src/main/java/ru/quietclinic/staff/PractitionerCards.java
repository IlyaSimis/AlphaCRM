package ru.quietclinic.staff;

import java.util.List;
import java.util.UUID;

/**
 * Порт к справочнику врачей: «Сотрудники» заводят карточку врача вместе с учётной записью, не
 * завися от кода расписания. Реализует модуль scheduling. Вызывается в транзакции создания
 * сотрудника: если учётную запись создать не удалось, карточка откатывается вместе с ней.
 */
public interface PractitionerCards {
  /** Создаёт карточку в клинике текущего сотрудника и возвращает её id. */
  UUID create(String fullName, String specialty, String room, List<ShiftSpec> shifts);
}
