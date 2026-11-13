import type { PatientItem } from '../../api/types'

function phoneDigits(value?: string | null) {
  const digits = (value ?? '').replace(/\D/g, '')
  return digits.length === 11 && digits.startsWith('8') ? `7${digits.slice(1)}` : digits
}

/** Поиск по ФИО, обоим телефонам и дате рождения без учёта регистра и формата номера. */
export function filterPatients(patients: PatientItem[], query: string) {
  const needle = query.trim().toLowerCase()
  if (!needle) return patients
  const phoneNeedle = phoneDigits(query)
  return patients.filter((patient) => {
    const text = `${patient.name} ${patient.phone} ${patient.secondPhone ?? ''} ${patient.birth}`.toLowerCase()
    return text.includes(needle) || (phoneNeedle.length > 0 && [patient.phone, patient.secondPhone].some((phone) => phoneDigits(phone).includes(phoneNeedle)))
  })
}
