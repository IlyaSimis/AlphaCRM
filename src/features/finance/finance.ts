import type { RefundReason, SalesGroup, SalesLine, VoidReason } from '../../api/types'
import { localToday, shiftDate } from '../../shared/dates'

export const voidReasonLabels: Record<VoidReason, string> = {
  DUPLICATE: 'Дубль счёта',
  ERROR: 'Ошибка в счёте',
  PATIENT_DECLINED: 'Пациент отказался',
  OTHER: 'Другое',
}

export const refundReasonLabels: Record<RefundReason, string> = {
  PATIENT_REQUEST: 'По просьбе пациента',
  SERVICE_NOT_PROVIDED: 'Услуга не оказана',
  ERROR: 'Ошибочная оплата',
  OTHER: 'Другое',
}

export const groupLabels: Record<SalesGroup, string> = { day: 'По дням', weekday: 'По дням недели', service: 'По услугам', practitioner: 'По врачам' }

export type PeriodPreset = 'month' | 'previous-month' | 'week' | 'custom'

export const presetLabels: Record<PeriodPreset, string> = {
  month: 'Этот месяц',
  'previous-month': 'Прошлый месяц',
  week: '7 дней',
  custom: 'Свой период',
}

/** Период по заготовке: даты YYYY-MM-DD включительно, «сегодня» — по часам клиники. */
export function periodOf(preset: Exclude<PeriodPreset, 'custom'>, today = localToday()): { from: string; to: string } {
  const [year, month] = today.split('-').map(Number)
  const first = (y: number, m: number) => `${y}-${String(m).padStart(2, '0')}-01`
  if (preset === 'week') return { from: shiftDate(today, -6), to: today }
  if (preset === 'month') return { from: first(year, month), to: today }
  const previousYear = month === 1 ? year - 1 : year
  const previousMonth = month === 1 ? 12 : month - 1
  return { from: first(previousYear, previousMonth), to: shiftDate(first(year, month), -1) }
}

/** Доля строки от самой большой — для полосы в таблице, 0–100. */
export function barWidth(line: SalesLine, lines: SalesLine[]) {
  const top = Math.max(0, ...lines.map((item) => item.netMinor))
  return top <= 0 ? 0 : Math.max(0, Math.round((line.netMinor / top) * 100))
}

/** Подпись строки: день — «28.10, ср», остальное — как есть. */
export function lineLabel(line: SalesLine, group: SalesGroup) {
  if (group === 'weekday') return ['', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье'][Number(line.key)] ?? line.label
  if (group !== 'day') return line.label
  const date = new Date(`${line.key}T12:00:00Z`)
  const day = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'][date.getUTCDay()]
  const [, month, dayOfMonth] = line.key.split('-')
  return `${dayOfMonth}.${month}, ${day}`
}
