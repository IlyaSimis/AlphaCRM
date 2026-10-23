import { describe, expect, it } from 'vitest'
import type { ScheduleItem } from '../../api/types'
import { historyRange, localHistory, splitHistory } from './history'

const item = (id: string, date: string, time: string, patientId = 'p1', name = 'Анна'): ScheduleItem => ({
  id, date, time, duration: '30 мин', minutes: 30, name, type: 'Приём', status: '', statusCode: 'BOOKED', tone: '', practitionerId: 'd', patientId,
})

describe('patient appointment history', () => {
  it('splits appointments into upcoming and past', () => {
    const { upcoming, past } = splitHistory([
      item('a', '2026-10-25', '10:00'),
      item('b', '2026-10-06', '09:00'),
      item('c', '2026-10-22', '09:00'),
      item('d', '2026-10-13', '11:00'),
    ], '2026-10-22')
    expect(upcoming.map((row) => row.id)).toEqual(['c', 'a'])
    expect(past.map((row) => row.id)).toEqual(['d', 'b'])
  })

  it('takes one year back and one year ahead', () => {
    expect(historyRange('2026-10-22')).toEqual({ from: '2025-10-22', to: '2027-10-22' })
  })

  it('in demo mode picks the patient appointments by id or name', () => {
    const items = [item('a', '2026-10-20', '09:00'), item('b', '2026-10-20', '10:00', 'p2', 'Дмитрий'), item('c', '2026-10-20', '11:00', '', 'Анна')]
    expect(localHistory(items, { id: 'p1', name: 'Анна' }).map((row) => row.id)).toEqual(['a', 'c'])
  })
})
