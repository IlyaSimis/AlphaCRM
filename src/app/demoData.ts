import { LEBEDEVA_ID, PETROV_ID } from '../api/client'
import { demoServices, statusLabel, toneFor } from '../api/schedule'
import type { AppointmentStatus, PatientItem, ScheduleItem } from '../api/types'

/** День, на который рассчитаны демо-данные. */
export const clinicDay = '2026-10-20'

const demoPatientIds: Record<string, string> = {
  'Анна Михайлова': 'P-10482',
  'Дмитрий Волков': 'P-10396',
  'Мария Соколова': 'P-10251',
}

function demoItem(id: string, date: string, time: string, minutes: number, name: string, type: string, statusCode: AppointmentStatus, practitionerId = PETROV_ID): ScheduleItem {
  const service = demoServices.find((item) => item.name === type)
  return {
    id, date, time, duration: `${minutes} мин`, minutes, name, type, status: statusLabel(statusCode), statusCode, tone: toneFor(statusCode), practitionerId,
    patientId: demoPatientIds[name] ?? '', amountMinor: service?.priceMinor ?? null, currency: service?.currency ?? null,
  }
}

export const demoAppointments: ScheduleItem[] = [
  demoItem('demo-301', clinicDay, '09:00', 50, 'Анна Михайлова', 'Первичный приём', 'ARRIVED'),
  demoItem('demo-302', clinicDay, '10:10', 30, 'Дмитрий Волков', 'Повторный приём', 'CONFIRMED'),
  demoItem('demo-303', clinicDay, '11:00', 50, 'Мария Соколова', 'Первичный приём', 'BOOKED'),
  demoItem('demo-304', clinicDay, '12:00', 30, 'Алексей Морозов', 'Повторный приём', 'CONFIRMED'),
  demoItem('demo-305', clinicDay, '15:00', 50, 'Ксения Орлова', 'Первичный приём', 'CONFIRMED', LEBEDEVA_ID),
  demoItem('demo-501', '2026-10-06', '09:00', 50, 'Анна Михайлова', 'Первичный приём', 'COMPLETED'),
  demoItem('demo-502', '2026-10-06', '10:10', 30, 'Дмитрий Волков', 'Повторный приём', 'COMPLETED'),
  demoItem('demo-503', '2026-10-06', '11:00', 50, 'Мария Соколова', 'Первичный приём', 'NO_SHOW', LEBEDEVA_ID),
  demoItem('demo-504', '2026-10-08', '12:00', 30, 'Анна Михайлова', 'Повторный приём', 'COMPLETED', LEBEDEVA_ID),
  demoItem('demo-505', '2026-10-08', '09:00', 30, 'Дмитрий Волков', 'Повторный приём', 'CANCELLED'),
  demoItem('demo-506', '2026-10-13', '09:00', 50, 'Мария Соколова', 'Первичный приём', 'COMPLETED'),
  demoItem('demo-507', '2026-10-13', '10:10', 30, 'Анна Михайлова', 'Повторный приём', 'COMPLETED'),
  demoItem('demo-508', '2026-10-13', '11:00', 30, 'Дмитрий Волков', 'Повторный приём', 'COMPLETED', LEBEDEVA_ID),
  demoItem('demo-509', '2026-10-15', '14:00', 30, 'Мария Соколова', 'Повторный приём', 'NO_SHOW', LEBEDEVA_ID),
  demoItem('demo-510', '2026-10-15', '09:00', 30, 'Анна Михайлова', 'Повторный приём', 'COMPLETED'),
]

export const demoPatients: PatientItem[] = [
  { name: 'Анна Михайлова', birth: '14.05.1991', birthDate: '1991-05-14', phone: '+7 916 245-18-72', id: 'P-10482', consent: 'Действует', consents: [{ id: 'demo-consent-1', version: 'v1.2', basis: 'SIGNED_FORM', grantedAt: '2026-01-15T09:00:00Z', revokedAt: null }] },
  { name: 'Дмитрий Волков', birth: '02.11.1985', birthDate: '1985-11-02', phone: '+7 903 782-44-16', id: 'P-10396', consent: 'Действует', consents: [{ id: 'demo-consent-2', version: 'v1.2', basis: 'SIGNED_FORM', grantedAt: '2026-02-03T09:00:00Z', revokedAt: null }] },
  { name: 'Мария Соколова', birth: '28.02.1996', birthDate: '1996-02-28', phone: '+7 925 663-02-41', id: 'P-10251', consent: 'Требует обновления', consents: [{ id: 'demo-consent-3', version: 'v1.1', basis: 'SIGNED_FORM', grantedAt: '2025-03-10T09:00:00Z', revokedAt: '2026-03-10T09:00:00Z' }] },
]
