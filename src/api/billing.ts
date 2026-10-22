import { API, failure, headersFor, type ApiRole } from './client'
import type { ChargeItem } from './types'

export async function loadCharges(role: ApiRole): Promise<ChargeItem[]> {
  const response = await fetch(`${API}/billing/charges`, { headers: headersFor(role) })
  if (!response.ok) throw await failure(response, 'Счета недоступны')
  return response.json()
}

export async function createCharge(input: { appointmentId: string; amountMinor: number; currency: string }, role: ApiRole): Promise<ChargeItem> {
  const response = await fetch(`${API}/billing/charges`, { method: 'POST', headers: headersFor(role), body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось выставить счёт')
  return response.json()
}

/** Оплата только кассиром. Ключ идемпотентности защищает от двойного списания при повторе. */
export async function payCharge(chargeId: string, idempotencyKey: string) {
  const response = await fetch(`${API}/billing/charges/${chargeId}/payments`, { method: 'POST', headers: headersFor('CASHIER'), body: JSON.stringify({ idempotencyKey }) })
  if (!response.ok) throw await failure(response, 'Не удалось провести оплату')
  return response.json()
}
