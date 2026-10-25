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
}

export function formatMoney(minor: number, currency = 'RUB') {
  return new Intl.NumberFormat('ru-RU', { style: 'currency', currency }).format(minor / 100)
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
    amountMinor: service.priceMinor,
    currency: service.currency,
  }
}
