import { failure, apiFetch } from './client'
import type { AssignedStaff, ConsentItem, PatientItem, PatientProfile, RegistryInput } from './types'

/** Карточка пациента целиком. Отсутствующий profile не затирает реквизиты в старых интеграциях. */
export type PatientInput = { fullName: string; birthDate: string; phone: string; email: string; secondPhone: string; assignedStaffId: string | null; profile?: PatientProfile }

export const emptyPatientProfile = (): PatientProfile => ({
  representatives: [], tags: [], emailNotifications: false, smsNotifications: false, messengerNotifications: false, pushNotifications: false,
})

export function normalizePatientProfile(profile?: PatientProfile | null): PatientProfile {
  return { ...emptyPatientProfile(), ...(profile ?? {}), representatives: profile?.representatives ?? [], tags: profile?.tags ?? [] }
}

/** Пустые строки не отправляются как даты и реквизиты; пустые представители удаляются. */
export function cleanPatientProfile(profile: PatientProfile): PatientProfile {
  const cleaned = Object.fromEntries(Object.entries(profile).map(([key, value]) => [key, typeof value === 'string' ? value.trim() || null : value])) as PatientProfile
  return {
    ...cleaned,
    tags: (profile.tags ?? []).map((tag) => tag.trim()).filter(Boolean),
    representatives: (profile.representatives ?? [])
      .map((item) => ({ fullName: item.fullName?.trim() || null, relationship: item.relationship?.trim() || null, primary: Boolean(item.primary) }))
      .filter((item) => item.fullName || item.relationship),
  }
}

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
  const response = await apiFetch(`/patients`)
  if (!response.ok) throw new Error('patients unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, unknown>) => {
    const patient = toPatient(row)
    rememberPatient(patient.id, patient.name)
    return patient
  })
}

export async function createPatient(input: PatientInput): Promise<PatientItem> {
  const response = await apiFetch(`/patients`, {
    method: 'POST',
    body: JSON.stringify({ ...input, consentVersion: 'v1.2', consentBasis: 'SIGNED_FORM' }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить пациента')
  const patient = toPatient(await response.json())
  rememberPatient(patient.id, patient.name)
  return patient
}

export async function updatePatient(id: string, input: PatientInput): Promise<PatientItem> {
  const response = await apiFetch(`/patients/${id}`, {
    method: 'PUT',
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
    referralSource: (row.referralSource as string | null | undefined) ?? null,
    registryNote: (row.registryNote as string | null | undefined) ?? null,
    phoneSharedWith: (row.phoneSharedWith as PatientItem['phoneSharedWith']) ?? [],
    telegramConnected: row.telegramConnected === true,
    maxConnected: row.maxConnected === true,
    preferredMessenger: (row.preferredMessenger as PatientItem['preferredMessenger'] | undefined) ?? null,
    email: (row.email as string | null | undefined) ?? null,
    secondPhone: (row.secondPhone as string | null | undefined) ?? null,
    assignedStaff: (row.assignedStaff as AssignedStaff | null | undefined) ?? null,
    profile: normalizePatientProfile(row.profile as PatientProfile | null | undefined),
  }
}

/** Источник, заметка регистратуры и Telegram. Пустой источник или заметка очищают поле. */
export async function updateRegistry(patientId: string, input: RegistryInput): Promise<PatientItem> {
  const response = await apiFetch(`/patients/${patientId}/registry`, { method: 'PUT', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить данные регистратуры')
  return toPatient(await response.json())
}

/** Как в поиске: действует, если есть согласие без revokedAt. */
export function consentStatus(consents: ConsentItem[]) {
  return consents.some((consent) => !consent.revokedAt) ? 'ACTIVE' : 'NEEDS_UPDATE'
}

export async function grantConsent(patientId: string, input: { version: string; basis: string }): Promise<PatientItem> {
  const response = await apiFetch(`/patients/${patientId}/consents`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить согласие')
  return toPatient(await response.json())
}

export async function revokeConsent(patientId: string, consentId: string): Promise<PatientItem> {
  const response = await apiFetch(`/patients/${patientId}/consents/${consentId}/revoke`, {
    method: 'POST',
  })
  if (!response.ok) throw await failure(response, 'Не удалось отозвать согласие')
  return toPatient(await response.json())
}

/** Пациент, созданный без сервера (демо-режим или сервер недоступен). */
export function localPatient(id: string, input: PatientInput, consents: ConsentItem[] = [newConsent('v1.2', 'SIGNED_FORM')], assignedStaff: AssignedStaff | null = null): PatientItem {
  return {
    id, name: input.fullName, birth: formatBirth(input.birthDate), birthDate: input.birthDate, phone: input.phone, consent: consentStatus(consents), consents,
    email: input.email.trim() || null, secondPhone: input.secondPhone.trim() || null, assignedStaff,
    profile: input.profile ? cleanPatientProfile(input.profile) : normalizePatientProfile(null),
  }
}

/** Простая проверка e-mail, как на сервере: что-то до @, домен с точкой, без пробелов. */
export function emailProblem(value: string) {
  const email = value.trim()
  if (!email) return null
  if (email.length > 255) return 'E-mail — не длиннее 255 символов'
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) ? null : 'Проверьте e-mail: например, name@example.ru'
}

export function newConsent(version: string, basis: string): ConsentItem {
  return { id: crypto.randomUUID(), version, basis, grantedAt: new Date().toISOString(), revokedAt: null }
}

function formatBirth(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}
