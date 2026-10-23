import { describe, expect, it } from 'vitest'
import { fromRows, minorToRubles, rublesToMinor, toRows } from './shifts'

describe('working hours and prices', () => {
  it('spreads hours over the week; other days are days off', () => {
    const rows = toRows([{ dayOfWeek: 2, startsAt: '09:00:00', endsAt: '18:00:00' }])
    expect(rows).toHaveLength(7)
    expect(rows[1]).toEqual({ day: 2, enabled: true, startsAt: '09:00', endsAt: '18:00' })
    expect(rows.filter((row) => row.enabled)).toHaveLength(1)
  })

  it('builds hours back and rejects a shift ending before it starts', () => {
    const rows = toRows([{ dayOfWeek: 2, startsAt: '09:00:00', endsAt: '18:00:00' }])
    rows[0] = { ...rows[0], enabled: true, startsAt: '10:00', endsAt: '14:00' }
    expect(fromRows(rows)).toEqual({ shifts: [{ dayOfWeek: 1, startsAt: '10:00:00', endsAt: '14:00:00' }, { dayOfWeek: 2, startsAt: '09:00:00', endsAt: '18:00:00' }] })
    rows[0] = { ...rows[0], endsAt: '09:00' }
    expect(fromRows(rows)).toEqual({ error: 'Понедельник: конец смены должен быть позже начала' })
  })

  it('converts rubles to kopecks and back', () => {
    expect(rublesToMinor('4 500,50')).toBe(450050)
    expect(rublesToMinor('3000')).toBe(300000)
    expect(rublesToMinor('-1')).toBeNull()
    expect(rublesToMinor('abc')).toBeNull()
    expect(minorToRubles(450000)).toBe('4500')
    expect(minorToRubles(450050)).toBe('4500,50')
  })
})
