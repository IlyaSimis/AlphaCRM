import { clinicParts } from './clinicTime'

/** Часть дня: от неё зависят приветствие и пейзаж фона. */
export type DayPeriod = 'morning' | 'day' | 'evening'

/** Час на часах клиники — тем же поясом, что clinicParts, а не поясом браузера. */
export function clinicHour(now: Date = new Date()) {
  return Number(clinicParts(now).time.slice(0, 2))
}

/** 5–11 — утро, 12–17 — день, 18–4 — вечер. */
export function dayPeriod(hour: number): DayPeriod {
  if (hour >= 5 && hour <= 11) return 'morning'
  if (hour >= 12 && hour <= 17) return 'day'
  return 'evening'
}

const phrases: Record<DayPeriod, string> = { morning: 'Доброе утро', day: 'Добрый день', evening: 'Добрый вечер' }

/** «Доброе утро, Анна» — по первому слову ФИО; без имени — одна фраза, без запятой. */
export function greeting(fullName: string | null | undefined, now: Date = new Date()) {
  const phrase = phrases[dayPeriod(clinicHour(now))]
  const first = (fullName ?? '').trim().split(/\s+/)[0]
  return first ? `${phrase}, ${first}` : phrase
}
