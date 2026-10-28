import { statusLabel } from '../../api/schedule'
import { clinicParts } from '../../shared/clinicTime'
import { channelLabels, templateLabels } from '../outbox/outboxLabels'

export const actionLabels: Record<string, string> = {
  PATIENT_ADMIN_READ: 'Просмотр карточки пациента',
  PATIENT_LIST_READ: 'Просмотр списка пациентов',
  PATIENT_CREATED: 'Регистрация пациента',
  PATIENT_UPDATED: 'Изменение данных пациента',
  PATIENT_CONSENT_CHANGED: 'Согласие на обработку данных',
  APPOINTMENT_CREATED: 'Создание записи',
  APPOINTMENT_CANCELLED: 'Отмена записи',
  APPOINTMENT_RESCHEDULED: 'Перенос записи',
  APPOINTMENT_STATUS_CHANGED: 'Смена статуса записи',
  APPOINTMENT_STATUS_CHANGE: 'Попытка сменить статус',
  ENCOUNTER_STARTED: 'Начало приёма',
  CLINICAL_HISTORY_READ: 'Просмотр клинической истории',
  CLINICAL_NOTE_SAVED: 'Сохранение заметки приёма',
  CLINICAL_ACCESS: 'Доступ к клиническим данным',
  DOCUMENT_DRAFT_CREATED: 'Черновик документа',
  DOCUMENT_SIGNED: 'Подписание документа',
  DOCUMENT_AMENDMENT_CREATED: 'Дополнение к документу',
  CHARGE_CREATED: 'Выставление счёта',
  PAYMENT_CONFIRMED: 'Оплата',
  PATIENT_REGISTRY_UPDATED: 'Данные регистратуры',
  REGISTRY_TASK_CREATED: 'Задача регистратуры',
  REGISTRY_TASK_DONE: 'Задача выполнена',
  REGISTRY_TASK_REOPENED: 'Задача снова открыта',
  WAITLIST_ADDED: 'Добавление в лист ожидания',
  WAITLIST_BOOKED: 'Запись из листа ожидания',
  WAITLIST_CANCELLED: 'Удаление из листа ожидания',
  PRACTITIONER_HOURS_CHANGED: 'Изменение графика врача',
  PRACTITIONER_CREATED: 'Новая карточка врача',
  PRACTITIONER_UPDATED: 'Изменение карточки врача',
  PDF_UPLOADED: 'Загрузка PDF пациента',
  PDF_SIGNED: 'Подписание PDF',
  PDF_READ: 'Скачивание PDF',
  SERVICE_PRICE_CHANGED: 'Изменение цены услуги',
  SERVICE_CREATED: 'Новая услуга',
  SERVICE_DEACTIVATED: 'Выключение услуги',
  SERVICE_ACTIVATED: 'Включение услуги',
  STAFF_CREATED: 'Новый сотрудник',
  STAFF_DEACTIVATED: 'Отключение сотрудника',
  STAFF_ACTIVATED: 'Включение сотрудника',
  STAFF_PASSWORD_RESET: 'Новый пароль сотрудника',
  STAFF_PASSWORD_CHANGED: 'Смена своего пароля',
  SIGNED_DOCUMENT_READ: 'Просмотр подписанного документа',
  NOTIFICATION_DELIVERED: 'Сообщение пациенту доставлено',
  FISCAL_RECEIPT_SENT: 'Чек передан в кассу',
  CHARGE_AMOUNT_SET: 'Сумма свободной цены',
  CHARGE_VOIDED: 'Аннулирование счёта',
  HR_CONTRACT_CHANGED: 'Изменение условий работы',
  HR_CONTRACT_READ: 'Просмотр условий работы',
  HR_CONTRACT_LIST_READ: 'Просмотр ставок',
  STAFF_ROLES_CHANGED: 'Изменение ролей',
  CLINICAL_BREAK_GLASS_GRANTED: 'Экстренный доступ к медданным',
  CLINICAL_BREAK_GLASS_LIST_READ: 'Просмотр журнала экстренных доступов',
  PAYMENT_REFUNDED: 'Возврат оплаты',
  SERVICE_NORMS_CHANGED: 'Нормы расхода услуги',
  OUTBOX_RETRY_REQUESTED: 'Повтор внешнего вывода',
  ITEM_CATEGORY_CREATED: 'Новая категория склада',
  ITEM_CATEGORY_UPDATED: 'Изменение категории склада',
  ITEM_CREATED: 'Новая позиция склада',
  ITEM_UPDATED: 'Изменение позиции склада',
  WAREHOUSE_CREATED: 'Новый склад',
  STOCK_DOCUMENT_CREATED: 'Новая накладная',
  STOCK_DOCUMENT_UPDATED: 'Изменение накладной',
  STOCK_DOCUMENT_POSTED: 'Проведение накладной',
  STOCK_DOCUMENT_CANCELLED: 'Отмена накладной',
  MARKING_RETRY_REQUESTED: 'Повтор операции маркировки',
  DRIVE_FOLDER_CREATED: 'Новая папка на диске',
  DRIVE_FILE_UPLOADED: 'Загрузка файла на диск',
  DRIVE_FILE_READ: 'Скачивание файла с диска',
  DRIVE_NODE_RENAMED: 'Переименование на диске',
  DRIVE_NODE_MOVED: 'Перенос на диске',
  DRIVE_NODE_TRASHED: 'Удаление в корзину',
  DRIVE_NODE_RESTORED: 'Восстановление из корзины',
  DRIVE_SHARE_CHANGED: 'Изменение доступа на диске',
  DRIVE_ACCESS: 'Доступ к файлу на диске',
  DRIVE_QUOTA_CHANGED: 'Изменение квоты диска',
}

export const objectLabels: Record<string, string> = {
  PATIENT: 'Пациент', APPOINTMENT: 'Запись', ENCOUNTER: 'Приём', DOCUMENT: 'Документ', CHARGE: 'Счёт', PAYMENT: 'Платёж', CONSENT: 'Согласие',
  TASK: 'Задача', WAITLIST: 'Лист ожидания', PRACTITIONER: 'Врач', SERVICE: 'Услуга', STAFF: 'Сотрудник', OUTBOX: 'Внешний вывод', CLINIC: 'Клиника', PATIENT_FILE: 'Файл PDF',
  ITEM: 'Позиция склада', ITEM_CATEGORY: 'Категория склада', WAREHOUSE: 'Склад', STOCK_DOCUMENT: 'Накладная', MARKING_OPERATION: 'Операция маркировки',
  DRIVE_NODE: 'Файл или папка диска', CLINIC_DRIVE: 'Диск клиники',
}

/** Коды, которые только повторяют колонку «Действие». */
const repeatsAction = new Set(['booking', 'cancel', 'treatment', 'attestation', 'correction', 'billing', 'registration'])

const fixed: Record<string, string> = {
  search: 'из поиска',
  card: 'из карточки',
  list: 'из списка',
  'no-changes': 'поля не изменились',
  'no-care-relationship': 'нет активной связи с лечением',
  granted: 'выдано',
  revoked: 'отозвано',
  'appointment-completed': 'после завершения приёма',
  'appointment-completed:open': 'после завершения приёма, свободная цена',
  'open-price': 'свободная цена',
  'break-glass': 'экстренный доступ',
  password: 'неверный пароль',
  PATIENT_COMPLAINT: 'жалоба пациента',
  LEGAL_REQUEST: 'запрос суда или органов',
  DOCTOR_UNAVAILABLE: 'лечащий врач недоступен',
  QUALITY_REVIEW: 'проверка качества',
  // Причины аннулирования и возврата — коды из списка, без свободного текста.
  DUPLICATE: 'дубль счёта',
  ERROR: 'ошибка',
  PATIENT_DECLINED: 'пациент отказался',
  OTHER: 'другое',
  PATIENT_REQUEST: 'по просьбе пациента',
  SERVICE_NOT_PROVIDED: 'услуга не оказана',
  'stock-shortage': 'нехватка на складе',
  FIXED: 'фиксированная цена',
  OPEN: 'свободная цена',
  direct: 'напрямую',
  link: 'по ссылке',
  // Старые события: до свободного текста задача выбиралась из двух видов.
  'call-back': 'перезвонить',
  'update-consent': 'обновить согласие',
  // Текст задачи в аудит не пишется — только есть ли у неё пациент.
  'with-patient': 'по пациенту',
  'without-patient': 'без пациента',
  self: 'сам сотрудник',
  // Действие пациента в Telegram-боте: запись, отмена, новая карточка.
  'telegram-bot': 'через Telegram-бота',
}

const roleNames: Record<string, string> = {
  OWNER: 'владелец', SENIOR_ADMINISTRATOR: 'старший администратор', ADMINISTRATOR: 'администратор', DOCTOR: 'врач', MANAGER: 'менеджер', CASHIER: 'кассир', NURSE: 'медперсонал', SUPPORT: 'техподдержка',
}

const dayNames = ['', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб', 'вс']

const fieldNames: Record<string, string> = {
  phone: 'телефон', fullName: 'ФИО', birthDate: 'дата рождения', referralSource: 'источник', registryNote: 'заметка регистратуры', telegram: 'Telegram',
  email: 'e-mail', secondPhone: 'второй телефон', assignedStaff: 'постоянный администратор',
  specialty: 'специальность', room: 'кабинет', shifts: 'график',
}

function clinicMoment(iso: string) {
  const { date, time } = clinicParts(iso)
  return `${date.slice(8, 10)}.${date.slice(5, 7)}.${date.slice(0, 4)} ${time}`
}

const statusCodes = new Set(['BOOKED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW', 'CANCELLED'])

const moment = String.raw`(\d{4})-(\d{2})-(\d{2})T(\d{2}:\d{2})Z`
const reschedulePattern = new RegExp(`^${moment}>${moment}(;practitioner)?$`)

/**
 * Русская подпись для машинного кода из колонки «Основание».
 * Неизвестный код показывается как есть — без догадок.
 */
export function reasonLabel(code: string | null | undefined): string {
  if (!code) return '—'
  if (repeatsAction.has(code)) return '—'
  if (fixed[code]) return fixed[code]

  const role = /^role:([A-Z_]+)$/.exec(code)
  if (role && roleNames[role[1]]) return `роль: ${roleNames[role[1]]}`
  const roles = /^roles:([A-Z_]+(,[A-Z_]+)*)$/.exec(code)
  if (roles && roles[1].split(',').every((item) => roleNames[item])) return `роли: ${roles[1].split(',').map((item) => roleNames[item]).join(', ')}`

  const days = /^days:([1-7](,[1-7])*)?$/.exec(code)
  if (days) return days[1] ? `рабочие дни: ${days[1].split(',').map((day) => dayNames[Number(day)]).join(', ')}` : 'рабочих дней нет'

  const fields = code.split(',')
  if (fields.every((field) => fieldNames[field])) return `изменены: ${fields.map((field) => fieldNames[field]).join(', ')}`

  // Карточка врача: «fields:fullName,specialty,room» — какие поля заданы, без значений.
  const filled = /^fields:(.+)$/.exec(code)
  if (filled && filled[1].split(',').every((field) => fieldNames[field])) return `поля: ${filled[1].split(',').map((field) => fieldNames[field]).join(', ')}`

  const transition = /^([A-Z_]+)->([A-Z_]+)$/.exec(code)
  if (transition && statusCodes.has(transition[1]) && statusCodes.has(transition[2])) {
    return `${statusLabel(transition[1])} → ${statusLabel(transition[2])}`
  }

  const attempt = /^to ([A-Z_]+)$/.exec(code)
  if (attempt && statusCodes.has(attempt[1])) return `попытка перевести в ${statusLabel(attempt[1])}`

  // Доставка из outbox: «шаблон:канал», без текста и адреса.
  const delivered = /^([a-z-]+):(webhook|telegram|cashbox)$/.exec(code)
  if (delivered && templateLabels[delivered[1]]) return `${templateLabels[delivered[1]]} · ${channelLabels[delivered[2].toUpperCase()]}`

  // Список пациентов: «list:12» — открыт весь список, «search:3» — поиск, найдено 3.
  const listed = /^(list|search):(\d+)$/.exec(code)
  if (listed) return listed[1] === 'list' ? `весь список, ${listed[2]}` : `поиск, найдено ${listed[2]}`

  // Перенос: время в коде — UTC, на экране — на часах клиники.
  const moved = reschedulePattern.exec(code)
  if (moved) {
    const [, y1, m1, d1, t1, y2, m2, d2, t2, doctorChanged] = moved
    return `${clinicMoment(`${y1}-${m1}-${d1}T${t1}:00Z`)} → ${clinicMoment(`${y2}-${m2}-${d2}T${t2}:00Z`)}${doctorChanged ? ', другой врач' : ''}`
  }

  return code
}
