import { describe, expect, it } from 'vitest'
import { daysBetween, formatDay, isoWeekday, shiftDate, shortDate, weekOf, weekStart } from './dates'

describe('dates', () => {
  it('shifts a date across month and year boundaries', () => {
    expect(shiftDate('2026-10-31', 1)).toBe('2026-11-01')
    expect(shiftDate('2027-01-01', -1)).toBe('2026-12-31')
  })

  it('formats a day in Russian', () => {
    expect(formatDay('2026-10-20')).toBe('20 октября 2026, вторник')
    expect(shortDate('2026-10-06')).toBe('06.10')
  })

  it('computes the ISO weekday', () => {
    expect(isoWeekday('2026-10-19')).toBe(1)
    expect(isoWeekday('2026-10-25')).toBe(7)
  })

  it('lists the days of a period inclusively', () => {
    expect(daysBetween('2026-10-30', '2026-11-02')).toEqual(['2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02'])
    expect(daysBetween('2026-10-02', '2026-10-01')).toEqual([])
  })

  it('the week starts on Monday', () => {
    expect(weekStart('2026-10-20')).toBe('2026-10-19')
    expect(weekStart('2026-10-25')).toBe('2026-10-19')
    expect(weekStart('2026-10-19')).toBe('2026-10-19')
    expect(weekOf('2026-11-01')).toEqual(['2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01'])
  })
})
