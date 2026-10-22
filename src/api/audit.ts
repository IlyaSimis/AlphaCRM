import { API, failure, headersFor, type ApiRole } from './client'
import type { AuditItem } from './types'

export async function loadAudit(role: ApiRole = 'MANAGER', result?: string): Promise<AuditItem[]> {
  const query = new URLSearchParams()
  if (result) query.set('result', result)
  const response = await fetch(`${API}/audit?${query}`, { headers: headersFor(role) })
  if (!response.ok) throw await failure(response, 'Журнал аудита недоступен')
  return response.json()
}
