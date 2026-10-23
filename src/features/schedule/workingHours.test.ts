import { describe, expect, it } from 'vitest'
import { demoPractitioners } from '../../api/schedule'
import { fitsWorkingHours, isDayOff, workingHoursLabel } from './workingHours'

const [petrov, lebedeva] = demoPractitioners

describe('practitioner working hours', () => {
  it('accepts an interval fully inside a shift', () => {
    expect(fitsWorkingHours(petrov, '2026-10-20', '09:00', 50)).toBe(true)
    expect(fitsWorkingHours(petrov, '2026-10-20', '17:30', 30)).toBe(true)
    expect(fitsWorkingHours(lebedeva, '2026-10-22', '18:30', 30)).toBe(true)
  })

  it('rejects going outside a shift and days without a shift', () => {
    expect(fitsWorkingHours(petrov, '2026-10-20', '08:45', 30)).toBe(false)
    expect(fitsWorkingHours(petrov, '2026-10-20', '17:45', 30)).toBe(false)
    expect(fitsWorkingHours(petrov, '2026-10-21', '10:00', 30)).toBe(false)
    expect(fitsWorkingHours(lebedeva, '2026-10-22', '09:30', 30)).toBe(false)
    expect(fitsWorkingHours(undefined, '2026-10-20', '10:00', 30)).toBe(false)
    expect(fitsWorkingHours(petrov, '2026-10-20', '', 30)).toBe(false)
  })

  it('labels a day change', () => {
    expect(workingHoursLabel(petrov, '2026-10-20')).toBe('09:00–18:00')
    expect(workingHoursLabel(petrov, '2026-10-21')).toBe('Выходной')
    expect(isDayOff(petrov, '2026-10-21')).toBe(true)
    expect(isDayOff(petrov, '2026-10-20')).toBe(false)
  })
})
