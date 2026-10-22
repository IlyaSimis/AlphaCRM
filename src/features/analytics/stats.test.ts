import { describe, expect, it } from 'vitest'
import type { StatRow } from '../../api/types'
import { bucketOf, rate, rowsFromSchedule, summarize } from './stats'

const rows: StatRow[] = [
  { date: '2026-10-06', practitionerId: 'petrov', status: 'COMPLETED', count: 2 },
  { date: '2026-10-06', practitionerId: 'lebedeva', status: 'NO_SHOW', count: 1 },
  { date: '2026-10-08', practitionerId: 'petrov', status: 'CANCELLED', count: 1 },
  { date: '2026-10-08', practitionerId: 'lebedeva', status: 'CONFIRMED', count: 3 },
  { date: '2026-11-01', practitionerId: 'petrov', status: 'COMPLETED', count: 9 },
]

describe('аналитика приёмов', () => {
  it('раскладывает статусы по итогам', () => {
    expect(bucketOf('ARRIVED')).toBe('attended')
    expect(bucketOf('IN_PROGRESS')).toBe('attended')
    expect(bucketOf('BOOKED')).toBe('planned')
    expect(bucketOf('NO_SHOW')).toBe('noShow')
    expect(bucketOf('CANCELLED')).toBe('cancelled')
  })

  it('считает итоги и доли только внутри периода', () => {
    const stats = summarize(rows, '2026-10-06', '2026-10-08')
    expect(stats.totals).toEqual({ attended: 2, planned: 3, noShow: 1, cancelled: 1, total: 7 })
    expect(stats.attendanceRate).toBe(67)
    expect(stats.noShowRate).toBe(33)
    expect(stats.cancelRate).toBe(14)
    expect(stats.days.map((day) => day.date)).toEqual(['2026-10-06', '2026-10-07', '2026-10-08'])
    expect(stats.days[1].total).toBe(0)
  })

  it('фильтрует по врачу и группирует по врачам', () => {
    const stats = summarize(rows, '2026-10-01', '2026-10-31', 'lebedeva')
    expect(stats.totals.total).toBe(4)
    expect([...stats.byPractitioner.keys()]).toEqual(['lebedeva'])
    expect(summarize(rows, '2026-10-01', '2026-10-31').byPractitioner.get('petrov')?.total).toBe(3)
  })

  it('без данных доли не считаются', () => {
    expect(rate(0, 0)).toBeNull()
    expect(summarize([], '2026-10-01', '2026-10-02').attendanceRate).toBeNull()
  })

  it('собирает строки статистики из расписания демо-режима', () => {
    const base = { time: '09:00', duration: '30 мин', minutes: 30, name: 'Анна', type: 'Приём', status: '', tone: '', practitionerId: 'petrov', patientId: 'p1' }
    const result = rowsFromSchedule([
      { ...base, id: '1', date: '2026-10-06', statusCode: 'COMPLETED' },
      { ...base, id: '2', date: '2026-10-06', statusCode: 'COMPLETED' },
      { ...base, id: '3', date: '2026-10-06', statusCode: 'NO_SHOW' },
    ])
    expect(result).toEqual([
      { date: '2026-10-06', practitionerId: 'petrov', status: 'COMPLETED', count: 2 },
      { date: '2026-10-06', practitionerId: 'petrov', status: 'NO_SHOW', count: 1 },
    ])
  })
})
