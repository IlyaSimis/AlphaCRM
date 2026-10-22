import type { PatientItem } from '../../api/types'

/** Поиск по ФИО, телефону и дате рождения без учёта регистра. */
export function filterPatients(patients: PatientItem[], query: string) {
  const needle = query.trim().toLowerCase()
  if (!needle) return patients
  return patients.filter((patient) => `${patient.name} ${patient.phone} ${patient.birth}`.toLowerCase().includes(needle))
}
