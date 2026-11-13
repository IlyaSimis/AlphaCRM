import { failure, apiFetch } from './client'
import type { ChargeItem, RefundReason, TerminalPaymentMethod, VoidReason } from './types'

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

/** Отправляет сумму на терминал; окончательный статус придёт отдельным уведомлением. */
export async function startTerminalPayment(chargeId: string, method: TerminalPaymentMethod) {
  const idempotencyKey = `terminal-${chargeId}-${method}-${crypto.randomUUID()}`
  const response = await apiFetch(`/billing/charges/${chargeId}/terminal-payments`, { method: 'POST', body: JSON.stringify({ idempotencyKey, method }) })
  if (!response.ok) throw await failure(response, 'Не удалось отправить оплату на терминал')
  return response.json() as Promise<{ paymentStatus: string }>
}

/** Аннулировать неоплаченный счёт — администраторы; причина — код из списка. */
export async function voidCharge(chargeId: string, reason: VoidReason): Promise<ChargeItem> {
  const response = await apiFetch(`/billing/charges/${chargeId}/void`, { method: 'POST', body: JSON.stringify({ reason }) })
  if (!response.ok) throw await failure(response, 'Не удалось аннулировать счёт')
  return response.json()
}

/** Полный возврат оплаты с чеком возврата — администраторы. Повтор с тем же ключом не вернёт дважды. */
export async function refundPayment(paymentId: string, reason: RefundReason, idempotencyKey: string) {
  const response = await apiFetch(`/billing/payments/${paymentId}/refunds`, { method: 'POST', body: JSON.stringify({ idempotencyKey, reason }) })
  if (!response.ok) throw await failure(response, 'Не удалось оформить возврат')
  return response.json()
}
