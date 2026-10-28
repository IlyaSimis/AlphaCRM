import type { BreakGlassReason } from '../../api/types'

export const breakGlassReasonLabels: Record<BreakGlassReason, string> = {
  PATIENT_COMPLAINT: 'Жалоба пациента',
  LEGAL_REQUEST: 'Запрос суда или органов',
  DOCTOR_UNAVAILABLE: 'Лечащий врач недоступен',
  QUALITY_REVIEW: 'Проверка качества помощи',
  OTHER: 'Другое',
}

/** Пояснение причины: от 10 символов (как на сервере). */
export const reasonTextProblem = (text: string) => (text.trim().length < 10 ? 'Опишите причину подробнее — от 10 символов' : null)

/** «до 14:32» — когда доступ закончится, по часам клиники. */
export function untilText(iso: string | null | undefined, timeZone?: string) {
  if (!iso) return ''
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : `до ${date.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit', timeZone })}`
}
