import { PETROV_ID } from '../../api/client'
import type { AppointmentStatus, ScheduleItem } from '../../api/types'
import type { Role } from '../../shared/roles'

export type StatusAction = { to: AppointmentStatus; label: string; from: AppointmentStatus[]; role: Role }

/** Переходы статуса записи по ролям. Зеркалит AppointmentStatusPolicy на backend. */
export const statusActions: StatusAction[] = [
  { to: 'CONFIRMED', label: 'Подтвердить запись', from: ['BOOKED'], role: 'ADMINISTRATOR' },
  { to: 'ARRIVED', label: 'Пациент пришёл', from: ['BOOKED', 'CONFIRMED'], role: 'ADMINISTRATOR' },
  { to: 'NO_SHOW', label: 'Отметить неявку', from: ['BOOKED', 'CONFIRMED'], role: 'ADMINISTRATOR' },
  { to: 'IN_PROGRESS', label: 'Пациент на приёме', from: ['ARRIVED'], role: 'DOCTOR' },
  { to: 'COMPLETED', label: 'Завершить приём', from: ['ARRIVED', 'IN_PROGRESS'], role: 'DOCTOR' },
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
  if (role === 'DOCTOR' && item.practitionerId !== doctorId) return []
  return statusActions.filter((action) => action.role === role && action.from.includes(item.statusCode))
}

export function canStartVisit(item: Pick<ScheduleItem, 'statusCode' | 'practitionerId'>, role: Role, doctorId: string) {
  return role === 'DOCTOR' && item.practitionerId === doctorId && STARTABLE.includes(item.statusCode)
}
