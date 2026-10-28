import { failure, apiFetch } from './client'
import type { ChargeItem } from './types'

export async function loadCharges(): Promise<ChargeItem[]> {
  const response = await apiFetch(`/billing/charges`)
  if (!response.ok) throw await failure(response, 'Счета недоступны')
  return response.json()
}

export async function createCharge(input: { appointmentId: string; amountMinor: number; currency: string }): Promise<ChargeItem> {
  const response = await apiFetch(`/billing/charges`, { method: 'POST', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось выставить счёт')
  return response.json()
}

/** Сумма свободной цены: в пределах услуги; после неё счёт можно оплатить. */
export async function setChargeAmount(chargeId: string, amountMinor: number): Promise<ChargeItem> {
  const response = await apiFetch(`/billing/charges/${chargeId}/amount`, { method: 'PUT', body: JSON.stringify({ amountMinor }) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить сумму')
  return response.json()
}

/** Оплата — кассир и администраторы. Ключ идемпотентности защищает от двойного списания при повторе. */
export async function payCharge(chargeId: string, idempotencyKey: string) {
  const response = await apiFetch(`/billing/charges/${chargeId}/payments`, { method: 'POST', body: JSON.stringify({ idempotencyKey }) })
  if (!response.ok) throw await failure(response, 'Не удалось провести оплату')
  return response.json()
}
