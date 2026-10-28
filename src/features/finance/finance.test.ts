import { describe, expect, it } from 'vitest'
import type { SalesLine } from '../../api/types'
import { barWidth, lineLabel, periodOf } from './finance'

const line = (key: string, netMinor: number): SalesLine => ({ key, label: key, payments: 1, amountMinor: netMinor, refunds: 0, refundedMinor: 0, netMinor, openPriceMinor: 0 })

describe('sales report', () => {
  it('periods: this month to today, previous month in full, the last seven days', () => {
    expect(periodOf('month', '2026-10-28')).toEqual({ from: '2026-10-01', to: '2026-10-28' })
    expect(periodOf('previous-month', '2026-10-28')).toEqual({ from: '2026-09-01', to: '2026-09-30' })
    expect(periodOf('previous-month', '2026-01-15')).toEqual({ from: '2025-12-01', to: '2025-12-31' })
    expect(periodOf('week', '2026-10-28')).toEqual({ from: '2026-10-22', to: '2026-10-28' })
  })

  it('bars are relative to the biggest revenue; refunds below zero give no bar', () => {
    const lines = [line('a', 1000), line('b', 250), line('c', -100)]
    expect(lines.map((item) => barWidth(item, lines))).toEqual([100, 25, 0])
    expect(barWidth(line('z', 0), [line('z', 0)])).toBe(0)
  })

  it('a day row reads as date and weekday', () => {
    expect(lineLabel(line('2026-10-28', 1), 'day')).toBe('28.10, ср')
    expect(lineLabel({ ...line('id', 1), label: 'Первичный приём' }, 'service')).toBe('Первичный приём')
  })
})
