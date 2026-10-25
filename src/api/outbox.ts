import { apiFetch, failure } from './client'
import type { OutboxItem } from './types'

/** Последние 100 внешних выводов своей клиники — только администратору. */
export async function loadOutbox(): Promise<OutboxItem[]> {
  const response = await apiFetch('/outbox')
  if (!response.ok) throw await failure(response, 'Внешние выводы недоступны')
  return response.json()
}
