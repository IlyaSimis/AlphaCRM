import { failure, apiFetch } from './client'
import type { AnalyticsOverview, StatRow } from './types'

export async function loadAppointmentStats(from: string, to: string): Promise<StatRow[]> {
  const query = new URLSearchParams({ from, to })
  const response = await apiFetch(`/analytics/appointments?${query}`)
  if (!response.ok) throw await failure(response, 'Аналитика недоступна')
  const body = await response.json()
  return body.rows as StatRow[]
}

/** Сводные метрики клиники без пациентских и клинических данных. */
export async function loadAnalyticsOverview(from: string, to: string, practitionerId?: string): Promise<AnalyticsOverview> {
  const query = new URLSearchParams({ from, to })
  if (practitionerId) query.set('practitionerId', practitionerId)
  const response = await apiFetch(`/analytics/overview?${query}`)
  if (!response.ok) throw await failure(response, 'Сводная аналитика недоступна')
  return response.json()
}
