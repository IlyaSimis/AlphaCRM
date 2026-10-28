import { apiFetch, failure } from './client'
import type { Dashboard } from './types'

/** Персональный дашборд: виджеты по роли, только числа и агрегаты. */
export async function loadDashboard(): Promise<Dashboard> {
  const response = await apiFetch('/me/dashboard')
  if (!response.ok) throw await failure(response, 'Дашборд недоступен')
  return response.json()
}
