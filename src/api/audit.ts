import { failure, apiFetch } from './client'
import type { AuditItem } from './types'

export async function loadAudit(result?: string): Promise<AuditItem[]> {
  const query = new URLSearchParams()
  if (result) query.set('result', result)
  const response = await apiFetch(`/audit?${query}`)
  if (!response.ok) throw await failure(response, 'Журнал аудита недоступен')
  return response.json()
}

/** Сколько событий журнала на странице. */
export const AUDIT_PAGE_SIZE = 20

export type AuditPage = { items: AuditItem[]; page: number; size: number; total: number }

/** Страница журнала: page с нуля, новые события сверху. */
export async function loadAuditPage(page: number, result?: string): Promise<AuditPage> {
  const query = new URLSearchParams({ page: String(page), size: String(AUDIT_PAGE_SIZE) })
  if (result) query.set('result', result)
  const response = await apiFetch(`/audit/page?${query}`)
  if (!response.ok) throw await failure(response, 'Журнал аудита недоступен')
  return response.json()
}
