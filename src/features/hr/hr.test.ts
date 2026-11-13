import { describe, expect, it } from 'vitest'
import { periodText, rateText } from './hr'

describe('staff terms', () => {
  it('shows the rate with its unit and the period of the terms', () => {
    expect(rateText({ rateMinor: 6000000, rateUnit: 'MONTH', currency: 'RUB' })).toMatch(/^60\s000,00\s₽ в месяц$/)
    expect(periodText({ validFrom: '2026-11-01', validTo: null })).toBe('с 01.11.2026')
    expect(periodText({ validFrom: '2026-01-01', validTo: '2026-10-31' })).toBe('01.01.2026 – 31.10.2026')
  })
})
