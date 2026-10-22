import { API, failure, headersFor, type ApiRole } from './client'
import type { StatRow } from './types'

export async function loadAppointmentStats(from: string, to: string, role: ApiRole = 'MANAGER'): Promise<StatRow[]> {
  const query = new URLSearchParams({ from, to })
  const response = await fetch(`${API}/analytics/appointments?${query}`, { headers: headersFor(role) })
  if (!response.ok) throw await failure(response, 'Аналитика недоступна')
  const body = await response.json()
  return body.rows as StatRow[]
}
