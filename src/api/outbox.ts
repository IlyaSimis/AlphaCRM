import { apiFetch, failure } from './client'
import type { OutboxItem } from './types'

/** Последние 100 внешних выводов своей клиники — только администратору; status — только с этим статусом. */
export async function loadOutbox(status?: OutboxItem['status']): Promise<OutboxItem[]> {
  const response = await apiFetch(status ? `/outbox?${new URLSearchParams({ status })}` : '/outbox')
  if (!response.ok) throw await failure(response, 'Внешние выводы недоступны')
  return response.json()
}
