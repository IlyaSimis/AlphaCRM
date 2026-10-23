import { failure, apiFetch } from './client'
import type { AuditItem } from './types'

export async function loadAudit(result?: string): Promise<AuditItem[]> {
  const query = new URLSearchParams()
  if (result) query.set('result', result)
  const response = await apiFetch(`/audit?${query}`)
  if (!response.ok) throw await failure(response, 'Журнал аудита недоступен')
  return response.json()
}
