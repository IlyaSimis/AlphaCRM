import { describe, expect, it } from 'vitest'
import type { ChargeItem, ScheduleItem } from '../../api/types'
import { buildCashierRows, localCharge, totals } from './cashier'

const visit = (id: string, date: string, statusCode: ScheduleItem['statusCode'] = 'COMPLETED', amountMinor: number | null = 300000): ScheduleItem => ({
  id, date, time: '09:00', duration: '30 мин', minutes: 30, name: 'Анна', type: 'Повторный приём', status: '', statusCode, tone: '', practitionerId: 'd', patientId: 'p', amountMinor, currency: 'RUB',
})
const charge = (appointmentId: string, status: string, amountMinor = 300000): ChargeItem => ({ chargeId: `c-${appointmentId}`, appointmentId, amountMinor, currency: 'RUB', status, createdAt: '' })

describe('cashier', () => {
  it('matches completed visits with charges and puts unpaid ones first', () => {
    const rows = buildCashierRows(
      [visit('a', '2026-10-06'), visit('b', '2026-10-13'), visit('c', '2026-10-15'), visit('x', '2026-10-15', 'CANCELLED')],
      [charge('a', 'PAID'), charge('b', 'PENDING', 450000)],
    )
    expect(rows.map((row) => [row.appointment.id, row.state])).toEqual([['c', 'NO_CHARGE'], ['b', 'PENDING'], ['a', 'PAID']])
    expect(totals(rows)).toEqual({ pending: 450000, paid: 300000, withoutCharge: 1, awaitingAmount: 0 })
  })

  it('a local charge takes the appointment price snapshot', () => {
    expect(localCharge(visit('a', '2026-10-06'))).toMatchObject({ appointmentId: 'a', amountMinor: 300000, status: 'PENDING' })
    expect(localCharge(visit('a', '2026-10-06', 'COMPLETED', null))).toBeNull()
  })

  it('an open-price visit gets a charge that waits for the amount and sorts before pending ones', () => {
    const open = { ...visit('o', '2026-10-20', 'COMPLETED', null), priceMode: 'OPEN' as const, estimateMinor: 200000 }
    expect(localCharge(open)).toMatchObject({ appointmentId: 'o', amountMinor: null, status: 'AWAITING_AMOUNT', estimateMinor: 200000 })
    const rows = buildCashierRows([visit('b', '2026-10-13'), open], [charge('b', 'PENDING'), { ...charge('o', 'AWAITING_AMOUNT'), amountMinor: null }])
    expect(rows.map((row) => row.state)).toEqual(['AWAITING_AMOUNT', 'PENDING'])
    expect(totals(rows).awaitingAmount).toBe(1)
  })
})
