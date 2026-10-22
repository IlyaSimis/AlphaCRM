import type { PatientItem, ScheduleItem } from '../../api/types'
import { shiftDate } from '../../shared/dates'

/** Период истории в карточке: год назад и год вперёд — без листания по дням. */
export function historyRange(today: string) {
  return { from: shiftDate(today, -365), to: shiftDate(today, 365) }
}

/** Записи пациента из уже загруженного расписания (демо-режим). */
export function localHistory(items: ScheduleItem[], patient: Pick<PatientItem, 'id' | 'name'>) {
  return items.filter((item) => (item.patientId ? item.patientId === patient.id : item.name === patient.name))
}

const byTime = (left: ScheduleItem, right: ScheduleItem) => left.date.localeCompare(right.date) || left.time.localeCompare(right.time)

/** Ближайшие — по возрастанию, прошедшие — от последней к первой. */
export function splitHistory(items: ScheduleItem[], today: string) {
  const sorted = [...items].sort(byTime)
  return {
    upcoming: sorted.filter((item) => item.date >= today),
    past: sorted.filter((item) => item.date < today).reverse(),
  }
}
