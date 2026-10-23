import { failure, apiFetch } from './client'
import type { DocumentSummary, EncounterView, NoteFields, SignedDocumentItem } from './types'

export const TEMPLATE_VERSION = 'v2.4'

/** Начинает приём по записи; повторный вызов возвращает уже начатый приём. */
export async function startEncounter(input: { appointmentId: string; patientId: string; practitionerId: string }): Promise<EncounterView> {
  const response = await apiFetch(`/clinical/encounters`, { method: 'POST', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось начать приём')
  return response.json()
}

/** История приёмов пациента с текстом заметок — только врачу с активной связью с лечением. */
export async function loadEncounters(patientId: string): Promise<EncounterView[]> {
  const response = await apiFetch(`/clinical/patients/${patientId}/encounters`)
  if (!response.ok) throw await failure(response, 'Клиническая история недоступна')
  return response.json()
}

export async function saveNote(encounterId: string, note: NoteFields): Promise<EncounterView> {
  const response = await apiFetch(`/clinical/encounters/${encounterId}/note`, { method: 'PUT', body: JSON.stringify(note) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить черновик')
  return response.json()
}

function toSummary(row: Record<string, unknown>): DocumentSummary {
  return {
    id: row.id as string,
    state: row.state as DocumentSummary['state'],
    templateVersion: row.templateVersion as string,
    signedAt: (row.signedAt as string | null) ?? null,
    amendsDocumentId: (row.amendsDocumentId as string | null) ?? null,
  }
}

/** Черновик документа из текущей заметки приёма. */
export async function createDocument(encounterId: string): Promise<DocumentSummary> {
  const response = await apiFetch(`/clinical/encounters/${encounterId}/documents`, { method: 'POST', body: JSON.stringify({ templateVersion: TEMPLATE_VERSION }) })
  if (!response.ok) throw await failure(response, 'Не удалось создать документ')
  return toSummary(await response.json())
}

export async function signDocument(documentId: string): Promise<DocumentSummary> {
  const response = await apiFetch(`/clinical/documents/${documentId}/sign`, { method: 'POST' })
  if (!response.ok) throw await failure(response, 'Не удалось подписать документ')
  return toSummary(await response.json())
}

/** Архив подписанных документов: врач — свои подписи, менеджер — все, можно сузить до врача. */
export async function loadSignedDocuments(practitionerId?: string): Promise<SignedDocumentItem[]> {
  const query = practitionerId ? `?${new URLSearchParams({ practitionerId })}` : ''
  const response = await apiFetch(`/clinical/signed-documents${query}`)
  if (!response.ok) throw await failure(response, 'Архив подписанных документов недоступен')
  return response.json()
}

export async function loadSignedDocument(id: string): Promise<SignedDocumentItem> {
  const response = await apiFetch(`/clinical/signed-documents/${id}`)
  if (!response.ok) throw await failure(response, 'Документ недоступен', { 404: 'Документ не найден' })
  return response.json()
}

/** Дополнение к подписанному документу: новый черновик, исходник остаётся заблокированным. */
export async function amendDocument(documentId: string): Promise<DocumentSummary> {
  const response = await apiFetch(`/clinical/documents/${documentId}/amendments`, { method: 'POST', body: JSON.stringify({ templateVersion: TEMPLATE_VERSION }) })
  if (!response.ok) throw await failure(response, 'Не удалось создать дополнение')
  return toSummary(await response.json())
}
