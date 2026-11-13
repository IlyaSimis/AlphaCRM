import { PETROV_ID } from '../../api/client'
import type { AppointmentStatus, ScheduleItem } from '../../api/types'
import { can } from '../../shared/permissions'
import type { Role } from '../../shared/roles'

/** desk — регистратура (schedule.appointment.write), doctor — врач по своей записи (write-own). */
export type StatusAction = { to: AppointmentStatus; label: string; from: AppointmentStatus[]; by: 'desk' | 'doctor' }

/** Переходы статуса записи. Зеркалит AppointmentStatusPolicy на backend. */
export const statusActions: StatusAction[] = [
  { to: 'CONFIRMED', label: 'Подтвердить запись', from: ['BOOKED'], by: 'desk' },
  { to: 'ARRIVED', label: 'Пациент пришёл', from: ['BOOKED', 'CONFIRMED'], by: 'desk' },
  { to: 'NO_SHOW', label: 'Отметить неявку', from: ['BOOKED', 'CONFIRMED'], by: 'desk' },
  { to: 'IN_PROGRESS', label: 'Пациент на приёме', from: ['ARRIVED'], by: 'doctor' },
  { to: 'COMPLETED', label: 'Завершить приём', from: ['ARRIVED', 'IN_PROGRESS'], by: 'doctor' },
]

/**
 * Статусы, из которых врач может начать приём (как на сервере): любая своя запись, кроме
 * отменённой, неявки и завершённой. Старт из «Ожидает» или «Подтверждён» переводит запись
 * в «На приёме»; несколько записей могут быть на приёме одновременно.
 */
export const STARTABLE: AppointmentStatus[] = ['BOOKED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS']

/** Перенести можно только ожидающую или подтверждённую запись. */
export const RESCHEDULABLE: AppointmentStatus[] = ['BOOKED', 'CONFIRMED']

/** Действия, доступные роли. Врач видит действия только по своим записям. */
export function availableActions(item: Pick<ScheduleItem, 'statusCode' | 'practitionerId'>, role: Role, doctorId = PETROV_ID) {
  const desk = can(role, 'schedule.appointment.write')
  const doctor = can(role, 'schedule.appointment.write-own') && item.practitionerId === doctorId
  return statusActions.filter((action) => ((action.by === 'desk' && desk) || (action.by === 'doctor' && doctor)) && action.from.includes(item.statusCode))
}

export function canStartVisit(item: Pick<ScheduleItem, 'statusCode' | 'practitionerId'>, role: Role, doctorId: string) {
  return can(role, 'clinical.note.write') && item.practitionerId === doctorId && STARTABLE.includes(item.statusCode)
}
