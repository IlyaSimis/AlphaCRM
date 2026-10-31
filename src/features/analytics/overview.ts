import type { AnalyticsLoad, AnalyticsWeek, Practitioner, ScheduleItem } from '../../api/types'
import { daysBetween } from '../../shared/dates'
import { bucketOf } from './stats'

export function loadRate(load: Pick<AnalyticsLoad, 'occupiedMinutes' | 'availableMinutes'>) {
  return load.availableMinutes > 0 ? Math.min(100, Math.round((load.occupiedMinutes / load.availableMinutes) * 100)) : null
}

export function waitlistConversion(booked: number, total: number) {
  return total > 0 ? Math.round((booked / total) * 100) : null
}

function monday(date: string) {
  const value = new Date(`${date}T12:00:00Z`)
  value.setUTCDate(value.getUTCDate() - ((value.getUTCDay() + 6) % 7))
  return value.toISOString().slice(0, 10)
}

function unionMinutes(intervals: Array<[number, number]>) {
  const sorted = intervals.filter(([start, end]) => end > start).sort((a, b) => a[0] - b[0])
  let total = 0
  let start = -1
  let end = -1
  for (const interval of sorted) {
    if (end < 0 || interval[0] > end) {
      if (end >= 0) total += end - start
      start = interval[0]
      end = interval[1]
    } else end = Math.max(end, interval[1])
  }
  return end < 0 ? 0 : total + end - start
}

function minutes(time: string) {
  const [hours, minutes] = time.split(':').map(Number)
  return hours * 60 + minutes
}

type Resource = { id: string; label: string; specialty: string | null; practitionerIds: string[] }

/** Демо-загрузка по тем же правилам, что на сервере: текущие смены и занятые минуты без отмен. */
export function demoOccupancy(items: ScheduleItem[], practitioners: Practitioner[], from: string, to: string, doctor?: string) {
  const selected = practitioners.filter((item) => !doctor || item.id === doctor)
  const doctorResources: Resource[] = selected.map((item) => ({ id: item.id, label: item.fullName, specialty: item.specialty, practitionerIds: [item.id] }))
  const roomNames = [...new Set(selected.map((item) => item.room).filter((room): room is string => Boolean(room)))]
  const roomResources: Resource[] = roomNames.map((room) => ({
    id: `room:${room}`, label: room, specialty: null,
    practitionerIds: selected.filter((item) => item.room === room).map((item) => item.id),
  }))
  const weeks = new Map<string, { occupied: number; available: number }>()
  for (let week = monday(from); week <= to; ) {
    weeks.set(week, { occupied: 0, available: 0 })
    const next = new Date(`${week}T12:00:00Z`)
    next.setUTCDate(next.getUTCDate() + 7)
    week = next.toISOString().slice(0, 10)
  }
  const calculate = (resources: Resource[], weekly: boolean) => resources.map((resource) => {
    let occupiedMinutes = 0
    let availableMinutes = 0
    for (const date of daysBetween(from, to)) {
      const dayOfWeek = new Date(`${date}T12:00:00Z`).getUTCDay() || 7
      const available = selected.filter((item) => resource.practitionerIds.includes(item.id))
        .flatMap((item) => item.workingHours.filter((shift) => shift.dayOfWeek === dayOfWeek)
          .map((shift): [number, number] => [minutes(shift.startsAt), minutes(shift.endsAt)]))
      const occupied = items.filter((item) => item.date === date && item.statusCode !== 'CANCELLED' && resource.practitionerIds.includes(item.practitionerId))
        .map((item): [number, number] => [minutes(item.time), minutes(item.time) + item.minutes])
      const dayAvailable = unionMinutes(available)
      const dayOccupied = unionMinutes(occupied)
      availableMinutes += dayAvailable
      occupiedMinutes += dayOccupied
      if (weekly) {
        const week = weeks.get(monday(date))
        if (week) { week.available += dayAvailable; week.occupied += dayOccupied }
      }
    }
    return { id: resource.id, label: resource.label, specialty: resource.specialty, occupiedMinutes, availableMinutes }
  })
  return { practitioners: calculate(doctorResources, true), rooms: calculate(roomResources, false), weeks }
}

/** Недельные тренды песочницы: записи и загрузка; выручка появляется при подключении API. */
export function demoWeeks(items: ScheduleItem[], practitioners: Practitioner[], from: string, to: string, doctor?: string): AnalyticsWeek[] {
  const occupancy = demoOccupancy(items, practitioners, from, to, doctor)
  return [...occupancy.weeks.entries()].map(([weekStart, load]) => {
    const weekEnd = new Date(`${weekStart}T12:00:00Z`)
    weekEnd.setUTCDate(weekEnd.getUTCDate() + 6)
    const end = weekEnd.toISOString().slice(0, 10)
    const selected = items.filter((item) => item.date >= weekStart && item.date <= end && item.date >= from && item.date <= to && (!doctor || item.practitionerId === doctor))
    return {
      weekStart,
      appointments: selected.length,
      attended: selected.filter((item) => bucketOf(item.statusCode) === 'attended').length,
      noShow: selected.filter((item) => item.statusCode === 'NO_SHOW').length,
      cancelled: selected.filter((item) => item.statusCode === 'CANCELLED').length,
      revenueMinor: 0,
      occupiedMinutes: load.occupied,
      availableMinutes: load.available,
    }
  })
}
