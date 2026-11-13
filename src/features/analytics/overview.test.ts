import { describe, expect, it } from 'vitest'
import type { Practitioner, ScheduleItem } from '../../api/types'
import { demoOccupancy, demoWeeks, loadRate, waitlistConversion } from './overview'

const practitioners: Practitioner[] = [
  { id: 'd1', fullName: 'Врач 1', specialty: 'Терапия', room: '101', workingHours: [{ dayOfWeek: 1, startsAt: '09:00', endsAt: '12:00' }] },
  { id: 'd2', fullName: 'Врач 2', specialty: 'Терапия', room: '101', workingHours: [{ dayOfWeek: 1, startsAt: '09:00', endsAt: '12:00' }] },
]
const visit = (id: string, practitionerId: string, time: string, minutes: number, statusCode: ScheduleItem['statusCode'] = 'COMPLETED'): ScheduleItem => ({
  id, practitionerId, patientId: `p-${id}`, date: '2026-10-19', time, minutes, duration: `${minutes} мин`, name: '', type: '', status: '', statusCode, tone: '',
})

describe('analytics overview calculations', () => {
  it('counts occupied intervals once and room capacity without duplication', () => {
    const result = demoOccupancy([
      visit('1', 'd1', '09:00', 60), visit('2', 'd1', '09:30', 30), visit('3', 'd1', '10:00', 30, 'CANCELLED'),
    ], practitioners, '2026-10-19', '2026-10-19')
    expect(result.practitioners[0]).toMatchObject({ occupiedMinutes: 60, availableMinutes: 180 })
    expect(result.practitioners[1]).toMatchObject({ occupiedMinutes: 0, availableMinutes: 180 })
    expect(result.rooms[0]).toMatchObject({ occupiedMinutes: 60, availableMinutes: 180 })
    expect(loadRate(result.practitioners[0])).toBe(33)
  })

  it('groups demo appointments and capacity into weeks', () => {
    const weeks = demoWeeks([visit('1', 'd1', '09:00', 60), visit('2', 'd1', '10:00', 30, 'NO_SHOW')], practitioners, '2026-10-19', '2026-10-25')
    expect(weeks).toHaveLength(1)
    expect(weeks[0]).toMatchObject({ weekStart: '2026-10-19', appointments: 2, attended: 1, noShow: 1, occupiedMinutes: 90, availableMinutes: 360 })
  })

  it('returns null instead of misleading zero rates without a denominator', () => {
    expect(loadRate({ occupiedMinutes: 0, availableMinutes: 0 })).toBeNull()
    expect(waitlistConversion(0, 0)).toBeNull()
    expect(waitlistConversion(3, 4)).toBe(75)
  })
})
