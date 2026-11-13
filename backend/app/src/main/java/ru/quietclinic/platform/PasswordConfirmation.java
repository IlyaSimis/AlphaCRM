package ru.quietclinic.platform;

/**
 * Повторный ввод пароля вошедшим сотрудником перед чувствительным действием (экстренный доступ).
 * Реализует модуль сотрудников: хеши паролей живут только там.
 */
public interface PasswordConfirmation {
  /** true — пароль совпал с паролем вошедшего сотрудника. */
  boolean confirm(String password);
}
