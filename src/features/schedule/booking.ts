import { statusLabel, toneFor } from '../../api/schedule'
import type { PatientItem, Practitioner, ScheduleItem, ServiceItem } from '../../api/types'
import { clinicInstant } from '../../shared/clinicTime'
import { OUTSIDE_WORKING_HOURS, fitsWorkingHours } from './workingHours'

export type BookingDraft = {
  patientId: string
  date: string
  time: string
  serviceId: string
  practitionerId: string
  /** Оценка суммы для свободной цены, в рублях; пусто — без оценки. */
  estimate?: string
}

export function formatMoney(minor: number, currency = 'RUB') {
  return new Intl.NumberFormat('ru-RU', { style: 'currency', currency }).format(minor / 100)
}

/** Цена услуги для экрана: сумма или пределы свободной цены. */
export function priceText(service: Pick<ServiceItem, 'priceMode' | 'priceMinor' | 'minMinor' | 'maxMinor' | 'currency'>) {
  if (service.priceMode === 'OPEN') {
    return `свободная цена ${formatMoney(service.minMinor ?? 0, service.currency)} – ${formatMoney(service.maxMinor ?? 0, service.currency)}`
  }
  return service.priceMinor == null ? '—' : formatMoney(service.priceMinor, service.currency)
}

/** Рубли из поля ввода («4500», «4500,50») в копейки; не число — null. */
export function rublesFromInput(value: string): number | null {
  const normalized = value.trim().replace(/\s/g, '').replace(',', '.')
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null
  return Math.round(Number(normalized) * 100)
}

/** Оценка свободной цены в копейках; пустое поле — null (без оценки). */
export function estimateOf(draft: BookingDraft): number | null {
  return draft.estimate && draft.estimate.trim() ? rublesFromInput(draft.estimate) : null
}

/** Услуги, которые можно выбрать в новой записи: только включённые. */
export function bookableServices(services: ServiceItem[]) {
  return services.filter((item) => item.active)
}

export const PAST_TIME = 'Это время уже прошло: выберите будущее'

/**
 * Проверяет черновик записи до отправки; возвращает текст ошибки или null. now — проверять, что
 * время не прошло (на сервере; демо-песочница живёт в своём дне и это не проверяет).
 */
export function validateBooking(draft: BookingDraft, patients: PatientItem[], services: ServiceItem[], practitioners: Practitioner[], now?: Date) {
  if (!patients.some((item) => item.id === draft.patientId)) return 'Выберите пациента из списка'
  if (!draft.date) return 'Укажите дату приёма'
  if (!draft.time) return 'Укажите время приёма'
  if (now && clinicInstant(draft.date, draft.time).getTime() < now.getTime()) return PAST_TIME
  const service = bookableServices(services).find((item) => item.id === draft.serviceId)
  if (!service) return 'Выберите услугу'
  const practitioner = practitioners.find((item) => item.id === draft.practitionerId)
  if (!practitioner) return 'Выберите врача'
  if (!fitsWorkingHours(practitioner, draft.date, draft.time, service.durationMinutes)) return OUTSIDE_WORKING_HOURS
  if (service.priceMode === 'OPEN' && draft.estimate && draft.estimate.trim()) {
    const estimate = estimateOf(draft)
    if (estimate === null) return 'Оценка — сумма в рублях, например 4500 или 4500,50'
    if (estimate < (service.minMinor ?? 0) || estimate > (service.maxMinor ?? Number.MAX_SAFE_INTEGER)) return 'Оценка должна быть в пределах свободной цены услуги'
  }
  return null
}

/** Строка расписания для записи, созданной без сервера. */
export function localBooking(draft: BookingDraft, patient: PatientItem, service: ServiceItem): ScheduleItem {
  return {
    id: crypto.randomUUID(),
    date: draft.date,
    time: draft.time,
    duration: `${service.durationMinutes} мин`,
    minutes: service.durationMinutes,
    name: patient.name,
    type: service.name,
    status: statusLabel('BOOKED'),
    statusCode: 'BOOKED',
    tone: toneFor('BOOKED'),
    practitionerId: draft.practitionerId,
    patientId: patient.id,
    amountMinor: service.priceMode === 'OPEN' ? null : service.priceMinor,
    currency: service.currency,
    priceMode: service.priceMode ?? 'FIXED',
    estimateMinor: service.priceMode === 'OPEN' ? estimateOf(draft) : null,
  }
}
