package ru.quietclinic.inventory;

import java.util.Set;
import java.util.UUID;

/**
 * Порт к оператору маркировки (раздел G): методы по смыслу операций, а не по кодам документов МДЛП
 * или True API ГИС МТ. Сейчас за портом — {@link MockMarkingAdapter} или «не настроено»; реальные
 * адаптеры (регистратор выбытия, API МДЛП, True API) сверяются по официальной документации при
 * подключении.
 */
public interface MarkingPort {
  /** Название адаптера для экрана «Маркировка»: NONE, MOCK, MDLP, GIS_MT. */
  String adapter();

  Result accept(UUID operationId, Set<String> sgtins);

  Result withdrawForMedicalUse(UUID operationId, Set<String> sgtins);

  Result writeOff(UUID operationId, Set<String> sgtins);

  Result returnToSupplier(UUID operationId, Set<String> sgtins);

  /**
   * Итог обмена. {@code accepted} — оператор принял; {@code retry} — временная ошибка, повторить
   * позже; иначе — отказ. errorCode — машинный код без персональных данных.
   */
  record Result(boolean accepted, boolean retry, String operatorRef, String errorCode) {
    static Result ok(String operatorRef) {
      return new Result(true, false, operatorRef, null);
    }

    static Result later(String errorCode) {
      return new Result(false, true, null, errorCode);
    }

    static Result rejected(String errorCode) {
      return new Result(false, false, null, errorCode);
    }
  }
}
