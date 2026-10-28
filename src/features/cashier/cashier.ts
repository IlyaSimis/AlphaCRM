import type { ChargeItem, ScheduleItem } from '../../api/types'

/** AWAITING_AMOUNT — счёт по свободной цене, сумму ещё не ввели; VOIDED — аннулирован; REFUNDED — оплата возвращена. */
export type CashierState = 'NO_CHARGE' | 'AWAITING_AMOUNT' | 'PENDING' | 'PAID' | 'REFUNDED' | 'VOIDED'

export type CashierRow = { appointment: ScheduleItem; charge?: ChargeItem; state: CashierState }

export const stateLabels: Record<CashierState, string> = {
  NO_CHARGE: 'Счёт не выставлен',
  AWAITING_AMOUNT: 'Ждёт суммы',
  PENDING: 'Ожидает оплаты',
  PAID: 'Оплачен',
  REFUNDED: 'Возврат',
  VOIDED: 'Аннулирован',
}

const knownStates = new Set<string>(['AWAITING_AMOUNT', 'PENDING', 'PAID', 'REFUNDED', 'VOIDED'])

/** Состояние строки кассы по счёту: нет счёта — NO_CHARGE, неизвестный статус — как PENDING. */
export function stateOf(charge: ChargeItem | undefined): CashierState {
  if (!charge) return 'NO_CHARGE'
  return knownStates.has(charge.status) ? (charge.status as CashierState) : 'PENDING'
}

/** Строки кассы: завершённые приёмы и счета по ним. Сначала неоплаченные, затем по дате. */
export function buildCashierRows(completed: ScheduleItem[], charges: ChargeItem[]): CashierRow[] {
  const byAppointment = new Map(charges.map((charge) => [charge.appointmentId, charge]))
  const order: Record<CashierState, number> = { NO_CHARGE: 0, AWAITING_AMOUNT: 1, PENDING: 2, PAID: 3, REFUNDED: 4, VOIDED: 5 }
  return completed
    .filter((item) => item.statusCode === 'COMPLETED' && item.id)
    .map((appointment): CashierRow => {
      const charge = byAppointment.get(appointment.id as string)
      const state = stateOf(charge)
      return { appointment, charge, state }
    })
    .sort((left, right) => order[left.state] - order[right.state] || right.appointment.date.localeCompare(left.appointment.date) || right.appointment.time.localeCompare(left.appointment.time))
}

export function totals(rows: CashierRow[]) {
  const sum = (state: CashierState) => rows.filter((row) => row.state === state).reduce((total, row) => total + (row.charge?.amountMinor ?? row.appointment.amountMinor ?? 0), 0)
  return { pending: sum('PENDING'), paid: sum('PAID'), refunded: sum('REFUNDED'), withoutCharge: rows.filter((row) => row.state === 'NO_CHARGE').length, awaitingAmount: rows.filter((row) => row.state === 'AWAITING_AMOUNT').length }
}

/** Счёт без сервера — так же, как его создаёт сервер при завершении приёма. */
export function localCharge(appointment: ScheduleItem): ChargeItem | null {
  if (!appointment.id) return null
  if (appointment.priceMode === 'OPEN') {
    return {
      chargeId: crypto.randomUUID(),
      appointmentId: appointment.id,
      amountMinor: null,
      currency: appointment.currency ?? 'RUB',
      status: 'AWAITING_AMOUNT',
      createdAt: new Date().toISOString(),
      paymentId: null,
      paymentStatus: null,
      priceMode: 'OPEN',
      estimateMinor: appointment.estimateMinor ?? null,
    }
  }
  if (appointment.amountMinor == null) return null
  return {
    chargeId: crypto.randomUUID(),
    appointmentId: appointment.id,
    amountMinor: appointment.amountMinor,
    currency: appointment.currency ?? 'RUB',
    status: 'PENDING',
    createdAt: new Date().toISOString(),
    paymentId: null,
    paymentStatus: null,
  }
}
