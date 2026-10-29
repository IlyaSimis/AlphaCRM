import { apiFetch, failure } from './client'

export type TelephonyStatus = { configured: boolean; defaultExtension: string }
export type CallItem = {
  id: string
  direction: 'INBOUND' | 'OUTBOUND'
  status: string
  extension: string | null
  occurredAt: string
  recordingAvailable: boolean
}

export async function loadTelephonyStatus(): Promise<TelephonyStatus> {
  const response = await apiFetch('/telephony/status')
  if (!response.ok) throw await failure(response, 'Не удалось проверить подключение Mango')
  return response.json()
}

export async function loadPatientCalls(patientId: string): Promise<CallItem[]> {
  const response = await apiFetch(`/telephony/patients/${patientId}/calls`)
  if (!response.ok) throw await failure(response, 'Не удалось загрузить историю звонков')
  return response.json()
}

export async function startPatientCall(patientId: string, extension: string): Promise<CallItem> {
  const response = await apiFetch('/telephony/calls', {
    method: 'POST',
    body: JSON.stringify({ patientId, extension }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось начать звонок через Mango')
  return response.json()
}
