import { API, failure, headersFor } from './client'
import type { ConsentItem, PatientItem } from './types'

export type PatientInput = { fullName: string; birthDate: string; phone: string }

/** Имена пациентов по id: API расписания возвращает только patientId. */
const names: Record<string, string> = {
  '00000000-0000-0000-0000-000000000101': 'Анна Михайлова',
  '00000000-0000-0000-0000-000000000102': 'Дмитрий Волков',
  '00000000-0000-0000-0000-000000000103': 'Мария Соколова',
}

export function rememberPatient(id: string, name: string) {
  if (id && name) names[id] = name
}

export function patientName(id: string) {
  return names[id] ?? 'Пациент'
}

export async function loadPatients(): Promise<PatientItem[]> {
  const response = await fetch(`${API}/patients`, { headers: headersFor() })
  if (!response.ok) throw new Error('patients unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, unknown>) => {
    const patient = toPatient(row)
    rememberPatient(patient.id, patient.name)
    return patient
  })
}

export async function createPatient(input: PatientInput): Promise<PatientItem> {
  const response = await fetch(`${API}/patients`, {
    method: 'POST',
    headers: headersFor('ADMINISTRATOR'),
    body: JSON.stringify({ ...input, consentVersion: 'v1.2', consentBasis: 'SIGNED_FORM' }),
  })
  if (!response.ok) throw new Error('patient create failed')
  const patient = toPatient(await response.json())
  rememberPatient(patient.id, patient.name)
  return patient
}

export async function updatePatient(id: string, input: PatientInput): Promise<PatientItem> {
  const response = await fetch(`${API}/patients/${id}`, {
    method: 'PUT',
    headers: headersFor('ADMINISTRATOR'),
    body: JSON.stringify(input),
  })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить изменения', { 404: 'Пациент не найден' })
  const patient = toPatient(await response.json())
  rememberPatient(patient.id, patient.name)
  return patient
}

export function toPatient(row: Record<string, unknown>): PatientItem {
  const contacts = row.contacts as Array<{ type: string; value: string }>
  const consents = (row.consents as ConsentItem[] | undefined) ?? []
  return {
    id: row.id as string,
    name: row.fullName as string,
    birth: formatBirth(row.birthDate as string),
    birthDate: row.birthDate as string,
    phone: contacts.find((contact) => contact.type === 'PHONE')?.value ?? '—',
    consent: consentStatus(consents),
    consents,
  }
}

/** Как в поиске: действует, если есть согласие без revokedAt. */
export function consentStatus(consents: ConsentItem[]) {
  return consents.some((consent) => !consent.revokedAt) ? 'Действует' : 'Требует обновления'
}

export async function grantConsent(patientId: string, input: { version: string; basis: string }): Promise<PatientItem> {
  const response = await fetch(`${API}/patients/${patientId}/consents`, {
    method: 'POST',
    headers: headersFor('ADMINISTRATOR'),
    body: JSON.stringify(input),
  })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить согласие')
  return toPatient(await response.json())
}

export async function revokeConsent(patientId: string, consentId: string): Promise<PatientItem> {
  const response = await fetch(`${API}/patients/${patientId}/consents/${consentId}/revoke`, {
    method: 'POST',
    headers: headersFor('ADMINISTRATOR'),
  })
  if (!response.ok) throw await failure(response, 'Не удалось отозвать согласие')
  return toPatient(await response.json())
}

/** Пациент, созданный без сервера (демо-режим или сервер недоступен). */
export function localPatient(id: string, input: PatientInput, consents: ConsentItem[] = [newConsent('v1.2', 'SIGNED_FORM')]): PatientItem {
  return { id, name: input.fullName, birth: formatBirth(input.birthDate), birthDate: input.birthDate, phone: input.phone, consent: consentStatus(consents), consents }
}

export function newConsent(version: string, basis: string): ConsentItem {
  return { id: crypto.randomUUID(), version, basis, grantedAt: new Date().toISOString(), revokedAt: null }
}

function formatBirth(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}
