import { describe, expect, it } from 'vitest'
import { daysBetween, formatDay, isoWeekday, shiftDate, shortDate } from './dates'

describe('даты', () => {
  it('сдвигает дату через границу месяца и года', () => {
    expect(shiftDate('2026-10-31', 1)).toBe('2026-11-01')
    expect(shiftDate('2027-01-01', -1)).toBe('2026-12-31')
  })

  it('форматирует день по-русски', () => {
    expect(formatDay('2026-10-20')).toBe('20 октября 2026, вторник')
    expect(shortDate('2026-10-06')).toBe('06.10')
  })

  it('считает день недели по ISO', () => {
    expect(isoWeekday('2026-10-19')).toBe(1)
    expect(isoWeekday('2026-10-25')).toBe(7)
  })

  it('перечисляет дни периода включительно', () => {
    expect(daysBetween('2026-10-30', '2026-11-02')).toEqual(['2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02'])
    expect(daysBetween('2026-10-02', '2026-10-01')).toEqual([])
  })
})
