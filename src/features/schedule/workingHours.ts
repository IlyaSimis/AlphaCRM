import type { Practitioner } from '../../api/types'
import { isoWeekday } from '../../shared/dates'

export const OUTSIDE_WORKING_HOURS = 'Время вне рабочих часов врача'

function minutesOf(time: string) {
  const [hours, minutes] = time.split(':').map(Number)
  return hours * 60 + minutes
}

/**
 * Та же проверка, что на сервере: весь интервал внутри смены врача в этот ISO-день недели.
 * Время — в шкале расписания (UTC), как и отправляется на сервер.
 */
export function fitsWorkingHours(practitioner: Practitioner | undefined, date: string, time: string, minutes: number) {
  if (!practitioner || !date || !/^\d{2}:\d{2}$/.test(time) || minutes <= 0) return false
  const start = minutesOf(time)
  const end = start + minutes
  if (end > 24 * 60) return false
  const day = isoWeekday(date)
  return practitioner.workingHours.some((shift) => shift.dayOfWeek === day && start >= minutesOf(shift.startsAt) && end <= minutesOf(shift.endsAt))
}

export function workingHoursLabel(practitioner: Practitioner, date: string) {
  const hours = practitioner.workingHours.find((item) => item.dayOfWeek === isoWeekday(date))
  return hours ? `${hours.startsAt.slice(0, 5)}–${hours.endsAt.slice(0, 5)}` : 'не принимает'
}
