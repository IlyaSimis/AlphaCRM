/**
 * Фразы для сотрудника по машинному коду ответа API. Сервер присылает code на английском и
 * английское пояснение; русский текст живёт только здесь. Кода, которого здесь нет, экран не
 * знает — тогда показывается сообщение сервера, а не сам code.
 */
export const errorMessages: Record<string, string> = {
  UNAUTHORIZED: 'Сессия закончилась. Войдите снова.',
  INVALID_CREDENTIALS: 'Неверный логин или пароль',
  CSRF_INVALID: 'Обновите страницу и повторите действие',
  ACCESS_DENIED: 'Недостаточно прав для операции',
  PASSWORD_CHANGE_REQUIRED: 'Сначала смените временный пароль',
  PASSWORD_NOT_CHANGED: 'Новый пароль должен отличаться от временного',
  WRONG_PASSWORD: 'Текущий пароль указан неверно',
  VALIDATION_ERROR: 'Проверьте заполнение полей',

  PATIENT_NOT_FOUND: 'Пациент не найден',
  PRACTITIONER_NOT_FOUND: 'Врач не найден',
  APPOINTMENT_NOT_FOUND: 'Запись не найдена',
  CONSENT_NOT_FOUND: 'Согласие не найдено',
  ENCOUNTER_NOT_FOUND: 'Приём не найден',
  DOCUMENT_NOT_FOUND: 'Документ не найден',
  SIGNED_DOCUMENT_NOT_FOUND: 'Документ не найден',
  STAFF_NOT_FOUND: 'Сотрудник не найден',
  CHARGE_NOT_FOUND: 'Счёт не найден',
  SERVICE_NOT_FOUND: 'Услуга не найдена',
  TASK_NOT_FOUND: 'Задача не найдена',
  WAITLIST_NOT_FOUND: 'Ожидание не найдено',

  SLOT_ALREADY_BOOKED: 'Интервал врача уже занят',
  OUTSIDE_WORKING_HOURS: 'Время вне рабочего графика врача',
  INVALID_INTERVAL: 'Окончание записи должно быть позже начала',
  UNKNOWN_SERVICE: 'Такой услуги нет в справочнике',
  SERVICE_INACTIVE: 'Услуга выключена и недоступна для новой записи',
  SERVICE_NAME_TAKEN: 'Услуга с таким названием уже есть',
  ALREADY_CANCELLED: 'Запись уже отменена',
  INVALID_STATUS_TRANSITION: 'Такой переход статуса невозможен',
  STATUS_CHANGE_FORBIDDEN: 'Ваша роль не может поставить этот статус',
  OWN_VISIT_ONLY: 'Врач начинает только свой приём',
  APPOINTMENT_NOT_STARTABLE: 'Этот приём нельзя начать',
  APPOINTMENT_MISMATCH: 'Пациент или врач не совпадают с записью',
  NO_CARE_RELATIONSHIP: 'Нет активной связи с лечением пациента',
  NOTE_REQUIRED: 'Сначала сохраните черновик заключения',
  DOCUMENT_LOCKED: 'Подписанный документ изменить нельзя',
  ORIGINAL_NOT_SIGNED: 'Дополнение можно сделать только к подписанному документу',
  INVALID_SHIFT: 'Проверьте смены: окончание позже начала, день недели один раз',
  INVALID_RANGE: 'Проверьте период',
  RANGE_TOO_LONG: 'Слишком длинный период',
  CHARGE_ALREADY_EXISTS: 'Счёт за этот приём уже выставлен',
  CHARGE_NOT_PAYABLE: 'Счёт уже оплачен',
  ALREADY_REVOKED: 'Согласие уже отозвано',
  INVALID_REFERRAL_SOURCE: 'Неизвестный источник обращения',
  INVALID_ASSIGNEE: 'Исполнитель не найден или отключён',
  TASK_CLOSE_FORBIDDEN: 'Закрыть задачу может исполнитель или администратор',
  INVALID_REFERENCE: 'Пациент, врач или услуга не найдены',
  WAITLIST_CLOSED: 'Ожидание уже закрыто',
  USERNAME_TAKEN: 'Такой логин уже есть',
  INVALID_ROLE: 'Неизвестная роль',
  PRACTITIONER_REQUIRED: 'Для врача выберите карточку врача',
  PRACTITIONER_TAKEN: 'У этого врача уже есть учётная запись',
  PRACTITIONER_NOT_ALLOWED: 'Карточка врача привязывается только к роли «Врач»',
  CANNOT_DEACTIVATE_SELF: 'Нельзя отключить свою учётную запись',
}

/** Фраза для сотрудника по коду ответа или undefined, если экран этот код ещё не знает. */
export function errorPhrase(code: string | undefined | null): string | undefined {
  return code ? errorMessages[code] : undefined
}
