import { ApiError, LEBEDEVA_ID, PETROV_ID, failure, apiFetch } from './client'
import { clinicInstant, clinicParts } from '../shared/clinicTime'
import { patientName, rememberPatient } from './patients'
import type { AppointmentStatus, Practitioner, ScheduleItem, ServiceItem, ServiceNorm } from './types'

export const demoPractitioners: Practitioner[] = [
  { id: PETROV_ID, fullName: 'Александр Петров', specialty: 'Психиатр', workingHours: [{ dayOfWeek: 2, startsAt: '09:00:00', endsAt: '18:00:00' }, { dayOfWeek: 4, startsAt: '09:00:00', endsAt: '18:00:00' }] },
  { id: LEBEDEVA_ID, fullName: 'Ольга Лебедева', specialty: 'Психотерапевт', workingHours: [{ dayOfWeek: 2, startsAt: '10:00:00', endsAt: '19:00:00' }, { dayOfWeek: 4, startsAt: '10:00:00', endsAt: '19:00:00' }] },
]

/**
 * Услуги демо-песочницы — те же, что начальные данные в Liquibase. На живом API форма записи
 * берёт справочник только с сервера.
 */
export const demoServices: ServiceItem[] = [
  { id: '00000000-0000-0000-0000-000000000601', name: 'Первичный приём', durationMinutes: 50, priceMinor: 450000, currency: 'RUB', active: true },
  { id: '00000000-0000-0000-0000-000000000602', name: 'Повторный приём', durationMinutes: 30, priceMinor: 300000, currency: 'RUB', active: true },
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
    date: date ?? clinicParts(start).date,
    time: clinicParts(start).time,
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
    priceMode: (row.priceMode as ScheduleItem['priceMode']) ?? null,
    estimateMinor: (row.estimateMinor as number | null) ?? null,
  }
}

export async function loadSchedule(date: string, patientId?: string): Promise<ScheduleItem[]> {
  const query = new URLSearchParams({ date })
  if (patientId) query.set('patientId', patientId)
  const response = await apiFetch(`/appointments?${query}`)
  if (!response.ok) throw new Error('schedule unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, string>) => toScheduleItem(row, date))
}

/** Записи за период: история пациента или завершённые приёмы для кассы. */
export async function loadAppointments(params: { from: string; to: string; patientId?: string; status?: AppointmentStatus }): Promise<ScheduleItem[]> {
  const query = new URLSearchParams({ from: params.from, to: params.to })
  if (params.patientId) query.set('patientId', params.patientId)
  if (params.status) query.set('status', params.status)
  const response = await apiFetch(`/appointments?${query}`)
  if (!response.ok) throw await failure(response, 'Не удалось загрузить записи')
  const rows = await response.json()
  return rows.map((row: Record<string, string>) => toScheduleItem(row))
}

/** Справочник услуг клиники; выключенные сервер отдаёт только администратору. */
export async function loadServices(): Promise<ServiceItem[]> {
  const response = await apiFetch(`/services?all=true`)
  if (!response.ok) throw await failure(response, 'Справочник услуг недоступен')
  const rows = (await response.json()) as ServiceItem[]
  return Array.isArray(rows) ? rows.map(toService) : []
}

/** Ответ сервера без поля active или priceMode (старая версия API) — включённая услуга с фиксированной ценой. */
function toService(row: ServiceItem): ServiceItem {
  return { ...row, active: row.active !== false, priceMode: row.priceMode ?? 'FIXED' }
}

/** Цена услуги: фиксированная сумма или пределы свободной цены. */
export type ServicePricing = { priceMode: 'FIXED'; priceMinor: number } | { priceMode: 'OPEN'; minMinor: number; maxMinor: number }

export async function createService(input: { name: string; durationMinutes: number } & ({ priceMinor: number; priceMode?: 'FIXED' } | { priceMode: 'OPEN'; minMinor: number; maxMinor: number })): Promise<ServiceItem> {
  const response = await apiFetch('/services', { method: 'POST', body: JSON.stringify({ ...input, currency: 'RUB' }) })
  if (!response.ok) throw await failure(response, 'Не удалось создать услугу')
  return toService(await response.json())
}

export async function setServiceActive(serviceId: string, active: boolean): Promise<ServiceItem> {
  const response = await apiFetch(`/services/${serviceId}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' })
  if (!response.ok) throw await failure(response, active ? 'Не удалось включить услугу' : 'Не удалось выключить услугу')
  return toService(await response.json())
}

export async function loadPractitioners(): Promise<Practitioner[]> {
  const response = await apiFetch(`/practitioners`)
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
  /** Оценка суммы для услуги со свободной ценой, в копейках; необязательна. */
  estimateMinor?: number | null
  /** ClientID посетителя сайта пациента; CRM не подставляет идентификатор браузера сотрудника. */
  metricaClientId?: string
}

export async function createAppointment(input: NewAppointment) {
  rememberPatient(input.patientId, input.patientName)
  const { startsAt, endsAt } = intervalOf(input.date, input.time, input.minutes)
  const response = await apiFetch(`/appointments`, {
    method: 'POST',
    body: JSON.stringify({
      patientId: input.patientId,
      practitionerId: input.practitionerId,
      startsAt,
      endsAt,
      serviceName: input.serviceName,
      ...(input.estimateMinor != null ? { estimateMinor: input.estimateMinor } : {}),
      ...(input.metricaClientId ? { metricaClientId: input.metricaClientId } : {}),
    }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось создать запись', { 409: 'Интервал врача уже занят' })
  return response.json()
}

/** Начало и конец интервала: дата и время — на часах клиники, ответ — моменты UTC. */
export function intervalOf(date: string, time: string, minutes: number) {
  const start = clinicInstant(date, time)
  if (Number.isNaN(start.getTime())) throw new ApiError('Некорректные дата или время', 400, 'INVALID_INTERVAL')
  return { startsAt: start.toISOString(), endsAt: new Date(start.getTime() + minutes * 60_000).toISOString() }
}

export async function rescheduleAppointment(id: string, input: { practitionerId: string; date: string; time: string; minutes: number }) {
  const response = await apiFetch(`/appointments/${id}/reschedule`, {
    method: 'POST',
    body: JSON.stringify({ practitionerId: input.practitionerId, ...intervalOf(input.date, input.time, input.minutes) }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось перенести запись', { 404: 'Запись не найдена' })
  return response.json()
}

/** Недельный график врача целиком; дня нет в списке — выходной. Только администратор. */
/** Специальность и кабинет врача; пустой кабинет стирает подпись. */
export async function savePractitionerCard(practitionerId: string, input: { specialty: string; room: string }): Promise<Practitioner> {
  const response = await apiFetch(`/practitioners/${practitionerId}`, { method: 'PUT', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить карточку врача')
  return response.json()
}

export async function saveWorkingHours(practitionerId: string, shifts: Practitioner['workingHours']): Promise<Practitioner> {
  const response = await apiFetch(`/practitioners/${practitionerId}/working-hours`, {
    method: 'PUT',
    body: JSON.stringify({ shifts: shifts.map((shift) => ({ dayOfWeek: shift.dayOfWeek, startsAt: shift.startsAt.slice(0, 5), endsAt: shift.endsAt.slice(0, 5) })) }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить график')
  return response.json()
}

/** Новая цена действует для новых записей; снимок цены уже созданных записей не меняется. */
export async function saveServicePrice(serviceId: string, priceMinor: number): Promise<ServiceItem> {
  const response = await apiFetch(`/services/${serviceId}/price`, { method: 'PUT', body: JSON.stringify({ priceMinor }) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить цену')
  return toService(await response.json())
}

/** Режим цены: фиксированная или свободная (с пределами). Действует для новых записей. */
export async function saveServicePricing(serviceId: string, pricing: ServicePricing): Promise<ServiceItem> {
  const response = await apiFetch(`/services/${serviceId}/pricing`, { method: 'PUT', body: JSON.stringify(pricing) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить цену')
  return toService(await response.json())
}

/** Нормы расхода услуги: склад спишет эти позиции при завершении приёма. */
export async function loadServiceNorms(serviceId: string): Promise<ServiceNorm[]> {
  const response = await apiFetch(`/services/${serviceId}/consumption-norms`)
  if (!response.ok) throw await failure(response, 'Нормы расхода недоступны')
  return response.json()
}

export async function saveServiceNorms(serviceId: string, norms: ServiceNorm[]): Promise<ServiceNorm[]> {
  const response = await apiFetch(`/services/${serviceId}/consumption-norms`, { method: 'PUT', body: JSON.stringify({ norms }) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить нормы расхода')
  return response.json()
}

export async function cancelAppointment(id: string) {
  const response = await apiFetch(`/appointments/${id}/cancel`, {
    method: 'POST',
  })
  if (!response.ok) throw await failure(response, 'Не удалось отменить запись', { 404: 'Запись не найдена' })
  return response.json()
}

export async function changeAppointmentStatus(id: string, status: AppointmentStatus) {
  const response = await apiFetch(`/appointments/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось изменить статус', { 404: 'Запись не найдена' })
  return response.json()
}
