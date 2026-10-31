import { apiFetch, failure } from './client'
import { toPatient } from './patients'
import type { PatientItem } from './types'

export type MessengerChannel = 'TELEGRAM' | 'MAX'
export type MessengerLink = { channel: MessengerChannel; token: string; url: string | null; expiresAt: string }

export async function createMessengerLink(patientId: string, channel: MessengerChannel): Promise<MessengerLink> {
  const response = await apiFetch(`/patients/${patientId}/messenger/link`, { method: 'POST', body: JSON.stringify({ channel }) })
  if (!response.ok) throw await failure(response, 'Не удалось создать ссылку привязки')
  return response.json()
}

export async function disconnectMessenger(patientId: string, channel: MessengerChannel): Promise<PatientItem> {
  const response = await apiFetch(`/patients/${patientId}/messenger/${channel}/disconnect`, { method: 'POST' })
  if (!response.ok) throw await failure(response, 'Не удалось отключить мессенджер')
  return toPatient(await response.json())
}

export async function preferMessenger(patientId: string, channel: MessengerChannel | ''): Promise<PatientItem> {
  const response = await apiFetch(`/patients/${patientId}/messenger/preference`, { method: 'PUT', body: JSON.stringify({ channel }) })
  if (!response.ok) throw await failure(response, 'Не удалось изменить основной канал')
  return toPatient(await response.json())
}
