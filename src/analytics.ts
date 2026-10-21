import type { AppointmentStatus, ScheduleItem, StatRow } from './api'

export type Bucket = 'attended' | 'planned' | 'noShow' | 'cancelled'
export type Counts = Record<Bucket, number> & { total: number }
export type DayStats = Counts & { date: string }

export const buckets: Array<{ key: Bucket; label: string }> = [
  { key: 'attended', label: 'Пришли' },
  { key: 'planned', label: 'Запланировано' },
  { key: 'noShow', label: 'Неявка' },
  { key: 'cancelled', label: 'Отменено' },
]

export function bucketOf(status: AppointmentStatus): Bucket {
  if (status === 'ARRIVED' || status === 'IN_PROGRESS' || status === 'COMPLETED') return 'attended'
  if (status === 'NO_SHOW') return 'noShow'
  if (status === 'CANCELLED') return 'cancelled'
  return 'planned'
}

function emptyCounts(): Counts {
  return { attended: 0, planned: 0, noShow: 0, cancelled: 0, total: 0 }
}

export function shiftIso(iso: string, days: number) {
  const [year, month, day] = iso.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

export function daysBetween(from: string, to: string) {
  const days: string[] = []
  for (let day = from; day <= to && days.length < 400; day = shiftIso(day, 1)) days.push(day)
  return days
}

export function rate(part: number, whole: number) {
  return whole > 0 ? Math.round((part / whole) * 100) : null
}

export function summarize(rows: StatRow[], from: string, to: string, practitionerId?: string) {
  const filtered = rows.filter((row) => row.date >= from && row.date <= to && (!practitionerId || row.practitionerId === practitionerId))
  const byDay = new Map(daysBetween(from, to).map((date) => [date, { date, ...emptyCounts() } as DayStats]))
  const byPractitioner = new Map<string, Counts>()
  const totals = emptyCounts()
  for (const row of filtered) {
    const bucket = bucketOf(row.status)
    const day = byDay.get(row.date)
    if (day) {
      day[bucket] += row.count
      day.total += row.count
    }
    const doctor = byPractitioner.get(row.practitionerId) ?? emptyCounts()
    doctor[bucket] += row.count
    doctor.total += row.count
    byPractitioner.set(row.practitionerId, doctor)
    totals[bucket] += row.count
    totals.total += row.count
  }
  return {
    days: [...byDay.values()],
    byPractitioner,
    totals,
    attendanceRate: rate(totals.attended, totals.attended + totals.noShow),
    noShowRate: rate(totals.noShow, totals.attended + totals.noShow),
    cancelRate: rate(totals.cancelled, totals.total),
  }
}

/** В демо-режиме аналитика строится по записям, которые уже есть в интерфейсе. */
export function rowsFromSchedule(items: ScheduleItem[]): StatRow[] {
  const grouped = new Map<string, StatRow>()
  for (const item of items) {
    const key = `${item.date}|${item.practitionerId}|${item.statusCode}`
    const row = grouped.get(key) ?? { date: item.date, practitionerId: item.practitionerId, status: item.statusCode, count: 0 }
    row.count += 1
    grouped.set(key, row)
  }
  return [...grouped.values()]
}
