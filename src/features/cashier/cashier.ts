import type { ChargeItem, ScheduleItem } from '../../api/types'

export type CashierState = 'NO_CHARGE' | 'PENDING' | 'PAID'

export type CashierRow = { appointment: ScheduleItem; charge?: ChargeItem; state: CashierState }

export const stateLabels: Record<CashierState, string> = {
  NO_CHARGE: 'Счёт не выставлен',
  PENDING: 'Ожидает оплаты',
  PAID: 'Оплачен',
}

/** Строки кассы: завершённые приёмы и счета по ним. Сначала неоплаченные, затем по дате. */
export function buildCashierRows(completed: ScheduleItem[], charges: ChargeItem[]): CashierRow[] {
  const byAppointment = new Map(charges.map((charge) => [charge.appointmentId, charge]))
  const order: Record<CashierState, number> = { NO_CHARGE: 0, PENDING: 1, PAID: 2 }
  return completed
    .filter((item) => item.statusCode === 'COMPLETED' && item.id)
    .map((appointment): CashierRow => {
      const charge = byAppointment.get(appointment.id as string)
      const state: CashierState = !charge ? 'NO_CHARGE' : charge.status === 'PAID' ? 'PAID' : 'PENDING'
      return { appointment, charge, state }
    })
    .sort((left, right) => order[left.state] - order[right.state] || right.appointment.date.localeCompare(left.appointment.date) || right.appointment.time.localeCompare(left.appointment.time))
}

export function totals(rows: CashierRow[]) {
  const sum = (state: CashierState) => rows.filter((row) => row.state === state).reduce((total, row) => total + (row.charge?.amountMinor ?? row.appointment.amountMinor ?? 0), 0)
  return { pending: sum('PENDING'), paid: sum('PAID'), withoutCharge: rows.filter((row) => row.state === 'NO_CHARGE').length }
}

/** Счёт без сервера — так же, как его создаёт сервер при завершении приёма. */
export function localCharge(appointment: ScheduleItem): ChargeItem | null {
  if (!appointment.id || appointment.amountMinor == null) return null
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
