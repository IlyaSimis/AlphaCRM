/** Подписи кодов outbox на экране. Неизвестный код показывается как есть. */
export const templateLabels: Record<string, string> = {
  'appointment-confirmed': 'Подтверждение записи',
  'appointment-reminder': 'Напоминание за сутки',
  'appointment-cancelled': 'Отмена записи',
  receipt: 'Чек',
  'refund-receipt': 'Чек возврата',
  unknown: 'Прежнее сообщение',
}

export const channelLabels: Record<string, string> = {
  WEBHOOK: 'Вебхук клиники',
  TELEGRAM: 'Telegram',
  CASHBOX: 'Онлайн-касса',
  MOCK: 'Не настроен',
}

export const statusLabels: Record<string, string> = {
  PENDING: 'Ожидает',
  SENDING: 'Отправляется',
  SENT: 'Доставлено',
  FAILED: 'Ошибка',
  CANCELLED: 'Отменено',
  SKIPPED: 'Не отправлено',
}

export const reasonLabels: Record<string, string> = {
  CHANNEL_NOT_CONFIGURED: 'канал не настроен',
  WEBHOOK_SECRET_MISSING: 'не задан секрет вебхука',
  NO_TELEGRAM_CONTACT: 'у пациента нет Telegram',
  FISCAL_NOT_CONFIGURED: 'касса не подключена',
  NO_CONSENT: 'нет действующего согласия',
  SHOWN_IN_BOT: 'подтверждение показано в боте',
  APPOINTMENT_CANCELLED: 'запись отменена',
  RESCHEDULED: 'запись перенесена',
  NO_SHOW: 'неявка',
  NOT_ACTIVE: 'приём уже начат или завершён',
  NOT_CANCELLED: 'запись не отменена',
  EXPIRED: 'время записи прошло',
  APPOINTMENT_NOT_FOUND: 'запись не найдена',
  PAYMENT_NOT_FOUND: 'платёж не найден',
  REFUND_NOT_FOUND: 'возврат не найден',
  NETWORK_ERROR: 'нет связи с получателем',
  DELIVERY_ERROR: 'ошибка доставки',
  DELIVERY_UNCERTAIN: 'неизвестно, дошло ли: без повтора',
}

export function reasonText(code: string | null | undefined) {
  if (!code) return ''
  if (reasonLabels[code]) return reasonLabels[code]
  const http = /^HTTP_(\d{3})$/.exec(code)
  if (http) return `получатель ответил ${http[1]}`
  return code
}

export const statusTone: Record<string, 'success' | 'warning' | 'error' | 'default'> = {
  SENT: 'success',
  PENDING: 'default',
  SENDING: 'default',
  FAILED: 'error',
  CANCELLED: 'default',
  SKIPPED: 'warning',
}
