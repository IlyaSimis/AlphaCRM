import type { Practitioner } from '../../api/types'

export type ShiftRow = { day: number; enabled: boolean; startsAt: string; endsAt: string }

export const dayLabels = ['', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье']

/** Семь строк недели: дня нет в графике — выходной. */
export function toRows(hours: Practitioner['workingHours']): ShiftRow[] {
  return [1, 2, 3, 4, 5, 6, 7].map((day) => {
    const shift = hours.find((item) => item.dayOfWeek === day)
    return shift
      ? { day, enabled: true, startsAt: shift.startsAt.slice(0, 5), endsAt: shift.endsAt.slice(0, 5) }
      : { day, enabled: false, startsAt: '09:00', endsAt: '18:00' }
  })
}

/** Строки формы в график для PUT; ошибка — та же, что вернёт сервер. */
export function fromRows(rows: ShiftRow[]): { shifts: Practitioner['workingHours'] } | { error: string } {
  const shifts: Practitioner['workingHours'] = []
  for (const row of rows.filter((item) => item.enabled)) {
    if (!/^\d{2}:\d{2}$/.test(row.startsAt) || !/^\d{2}:\d{2}$/.test(row.endsAt) || row.endsAt <= row.startsAt) {
      return { error: `${dayLabels[row.day]}: конец смены должен быть позже начала` }
    }
    shifts.push({ dayOfWeek: row.day, startsAt: `${row.startsAt}:00`, endsAt: `${row.endsAt}:00` })
  }
  return { shifts }
}

/** «4 500,50» → 450050 копеек; null, если это не неотрицательная сумма. */
export function rublesToMinor(value: string) {
  const normalized = value.replace(/\s/g, '').replace(',', '.')
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null
  return Math.round(Number(normalized) * 100)
}

export function minorToRubles(minor: number) {
  return minor % 100 === 0 ? String(minor / 100) : (minor / 100).toFixed(2).replace('.', ',')
}
