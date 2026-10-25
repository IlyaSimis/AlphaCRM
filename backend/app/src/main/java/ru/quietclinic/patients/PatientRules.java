package ru.quietclinic.patients;

/** Правила полей карточки, общие для регистрации и правки. */
final class PatientRules {
  /**
   * Простая проверка e-mail: что-то до @, домен с точкой, без пробелов. Пустая строка допустима —
   * она убирает e-mail из карточки.
   */
  static final String EMAIL = "^$|^\\s*[^@\\s]+@[^@\\s]+\\.[^@\\s]+\\s*$";

  private PatientRules() {}
}
