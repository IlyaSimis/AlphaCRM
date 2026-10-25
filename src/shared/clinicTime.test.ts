import { afterEach, describe, expect, it } from 'vitest'
import { clinicInstant, clinicParts, clinicTimeZone, setClinicTimeZone } from './clinicTime'

afterEach(() => setClinicTimeZone('UTC'))

describe('clinic time zone', () => {
  it('keeps UTC by default, as the schedule always showed it', () => {
    expect(clinicTimeZone()).toBe('UTC')
    expect(clinicParts('2026-10-20T09:00:00Z')).toEqual({ date: '2026-10-20', time: '09:00' })
    expect(clinicInstant('2026-10-20', '09:00').toISOString()).toBe('2026-10-20T09:00:00.000Z')
  })

  it('shows and books in the clinic zone', () => {
    setClinicTimeZone('Europe/Moscow')
    expect(clinicParts('2026-10-20T06:00:00Z')).toEqual({ date: '2026-10-20', time: '09:00' })
    expect(clinicParts('2026-10-20T22:30:00Z')).toEqual({ date: '2026-10-21', time: '01:30' })
    expect(clinicInstant('2026-10-20', '09:00').toISOString()).toBe('2026-10-20T06:00:00.000Z')
  })

  it('handles daylight saving time', () => {
    setClinicTimeZone('Europe/Berlin')
    expect(clinicInstant('2026-07-01', '10:00').toISOString()).toBe('2026-07-01T08:00:00.000Z')
    expect(clinicInstant('2026-12-01', '10:00').toISOString()).toBe('2026-12-01T09:00:00.000Z')
  })

  it('falls back to UTC for an unknown zone', () => {
    setClinicTimeZone('Mars/Olympus')
    expect(clinicTimeZone()).toBe('UTC')
  })
})
