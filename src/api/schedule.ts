import { API, ApiError, LEBEDEVA_ID, PETROV_ID, failure, headersFor, type ApiRole } from './client'
import { patientName, rememberPatient } from './patients'
import type { AppointmentStatus, Practitioner, ScheduleItem, ServiceItem } from './types'

export const demoPractitioners: Practitioner[] = [
  { id: PETROV_ID, fullName: 'Александр Петров', specialty: 'Психиатр', workingHours: [{ dayOfWeek: 2, startsAt: '09:00:00', endsAt: '18:00:00' }, { dayOfWeek: 4, startsAt: '09:00:00', endsAt: '18:00:00' }] },
  { id: LEBEDEVA_ID, fullName: 'Ольга Лебедева', specialty: 'Психотерапевт', workingHours: [{ dayOfWeek: 2, startsAt: '10:00:00', endsAt: '19:00:00' }, { dayOfWeek: 4, startsAt: '10:00:00', endsAt: '19:00:00' }] },
]

/** Справочник услуг на случай, если сервер недоступен. Совпадает с seed в Liquibase. */
export const demoServices: ServiceItem[] = [
  { id: '00000000-0000-0000-0000-000000000601', name: 'Первичный приём', durationMinutes: 50, priceMinor: 450000, currency: 'RUB' },
  { id: '00000000-0000-0000-0000-000000000602', name: 'Повторный приём', durationMinutes: 30, priceMinor: 300000, currency: 'RUB' },
]

const labels: Record<AppointmentStatus, string> = {
  BOOKED: 'Ожидает', CONFIRMED: 'Подтверждён', ARRIVED: 'Прибыл', IN_PROGRESS: 'На приёме', COMPLETED: 'Завершён', NO_SHOW: 'Неявка', CANCELLED: 'Отменена',
}

const tones: Record<AppointmentStatus, string> = {
  BOOKED: 'orange', CONFIRMED: 'blue', ARRIVED: 'green', IN_PROGRESS: 'green', COMPLETED: 'done', NO_SHOW: 'red', CANCELLED: 'muted',
}

export function statusLabel(status: string) {
  return labels[status as AppointmentStatus] ?? status
}

export function toneFor(status: AppointmentStatus) {
  return tones[status] ?? 'blue'
}

/** Записи, которые ещё можно отменить. */
export const CANCELLABLE: AppointmentStatus[] = ['BOOKED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS']

export function withStatus(item: ScheduleItem, status: AppointmentStatus): ScheduleItem {
  return { ...item, status: statusLabel(status), statusCode: status, tone: toneFor(status) }
}

export function toScheduleItem(row: Record<string, string | number | null>, date?: string): ScheduleItem {
  const startsAt = String(row.startsAt)
  const start = new Date(startsAt)
  const end = new Date(String(row.endsAt))
  const minutes = Math.round((end.getTime() - start.getTime()) / 60000)
  const statusCode = row.status as AppointmentStatus
  const patientId = String(row.patientId ?? '')
  return {
    id: row.id as string,
    date: date ?? startsAt.slice(0, 10),
    time: start.toISOString().slice(11, 16),
    duration: `${minutes} мин`,
    minutes,
    name: patientName(patientId),
    type: row.serviceName as string,
    status: statusLabel(statusCode),
    statusCode,
    tone: toneFor(statusCode),
    practitionerId: (row.practitionerId as string) ?? PETROV_ID,
    patientId,
    amountMinor: (row.amountMinor as number | null) ?? null,
    currency: (row.currency as string | null) ?? null,
  }
}

export async function loadSchedule(date: string, patientId?: string): Promise<ScheduleItem[]> {
  const query = new URLSearchParams({ date })
  if (patientId) query.set('patientId', patientId)
  const response = await fetch(`${API}/appointments?${query}`, { headers: headersFor() })
  if (!response.ok) throw new Error('schedule unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, string>) => toScheduleItem(row, date))
}

/** Записи за период: история пациента или завершённые приёмы для кассы. */
export async function loadAppointments(params: { from: string; to: string; patientId?: string; status?: AppointmentStatus }): Promise<ScheduleItem[]> {
  const query = new URLSearchParams({ from: params.from, to: params.to })
  if (params.patientId) query.set('patientId', params.patientId)
  if (params.status) query.set('status', params.status)
  const response = await fetch(`${API}/appointments?${query}`, { headers: headersFor() })
  if (!response.ok) throw await failure(response, 'Не удалось загрузить записи')
  const rows = await response.json()
  return rows.map((row: Record<string, string>) => toScheduleItem(row))
}

export async function loadServices(): Promise<ServiceItem[]> {
  const response = await fetch(`${API}/services`, { headers: headersFor() })
  if (!response.ok) throw new Error('services unavailable')
  const rows = (await response.json()) as ServiceItem[]
  if (!Array.isArray(rows) || rows.length === 0) throw new Error('services empty')
  return rows
}

export async function loadPractitioners(): Promise<Practitioner[]> {
  const response = await fetch(`${API}/practitioners`, { headers: headersFor() })
  if (!response.ok) throw new Error('practitioners unavailable')
  const rows = (await response.json()) as Practitioner[]
  if (!Array.isArray(rows) || rows.length === 0) throw new Error('practitioners empty')
  return rows
}

export type NewAppointment = {
  patientId: string
  patientName: string
  practitionerId: string
  date: string
  time: string
  minutes: number
  serviceName: string
}

export async function createAppointment(input: NewAppointment, role: ApiRole = 'DOCTOR') {
  rememberPatient(input.patientId, input.patientName)
  const { startsAt, endsAt } = intervalOf(input.date, input.time, input.minutes)
  const response = await fetch(`${API}/appointments`, {
    method: 'POST',
    headers: headersFor(role),
    body: JSON.stringify({
      patientId: input.patientId,
      practitionerId: input.practitionerId,
      startsAt,
      endsAt,
      serviceName: input.serviceName,
    }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось создать запись', { 409: 'Интервал врача уже занят' })
  return response.json()
}

/** Начало и конец интервала в той же шкале, что и расписание: UTC. */
export function intervalOf(date: string, time: string, minutes: number) {
  const start = new Date(`${date}T${time}:00Z`)
  if (Number.isNaN(start.getTime())) throw new ApiError('Некорректные дата или время', 400, 'INVALID_INTERVAL')
  return { startsAt: start.toISOString(), endsAt: new Date(start.getTime() + minutes * 60_000).toISOString() }
}

export async function rescheduleAppointment(id: string, input: { practitionerId: string; date: string; time: string; minutes: number }) {
  const response = await fetch(`${API}/appointments/${id}/reschedule`, {
    method: 'POST',
    headers: headersFor('ADMINISTRATOR'),
    body: JSON.stringify({ practitionerId: input.practitionerId, ...intervalOf(input.date, input.time, input.minutes) }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось перенести запись', { 404: 'Запись не найдена' })
  return response.json()
}

export async function cancelAppointment(id: string) {
  const response = await fetch(`${API}/appointments/${id}/cancel`, {
    method: 'POST',
    headers: headersFor('ADMINISTRATOR'),
  })
  if (!response.ok) throw await failure(response, 'Не удалось отменить запись', { 404: 'Запись не найдена' })
  return response.json()
}

export async function changeAppointmentStatus(id: string, status: AppointmentStatus, role: ApiRole) {
  const response = await fetch(`${API}/appointments/${id}/status`, {
    method: 'POST',
    headers: headersFor(role),
    body: JSON.stringify({ status }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось изменить статус', { 404: 'Запись не найдена' })
  return response.json()
}
