import { failure, apiFetch } from './client'
import type { StatRow } from './types'

export async function loadAppointmentStats(from: string, to: string): Promise<StatRow[]> {
  const query = new URLSearchParams({ from, to })
  const response = await apiFetch(`/analytics/appointments?${query}`)
  if (!response.ok) throw await failure(response, 'Аналитика недоступна')
  const body = await response.json()
  return body.rows as StatRow[]
}
