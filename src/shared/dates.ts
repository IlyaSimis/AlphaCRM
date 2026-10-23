const weekdays = ['воскресенье', 'понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота']
const months = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря']

function parseIso(iso: string) {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day))
}

/** Сдвигает дату `YYYY-MM-DD` на указанное число дней. */
export function shiftDate(iso: string, days: number) {
  const date = parseIso(iso)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

/** «20 октября 2026, вторник». */
export function formatDay(iso: string) {
  const [year, month, day] = iso.split('-').map(Number)
  return `${day} ${months[month - 1]} ${year}, ${weekdays[parseIso(iso).getUTCDay()]}`
}

/** «20.10». */
export function shortDate(iso: string) {
  const [, month, day] = iso.split('-')
  return `${day}.${month}`
}

/** День недели по ISO: понедельник = 1, воскресенье = 7. */
export function isoWeekday(iso: string) {
  const value = parseIso(iso).getUTCDay()
  return value === 0 ? 7 : value
}

/** Все даты от `from` до `to` включительно. */
export function daysBetween(from: string, to: string) {
  const days: string[] = []
  for (let day = from; day <= to && days.length < 400; day = shiftDate(day, 1)) days.push(day)
  return days
}

/** Понедельник недели, в которую попадает дата. */
export function weekStart(iso: string) {
  return shiftDate(iso, 1 - isoWeekday(iso))
}

/** Семь дат недели с понедельника. */
export function weekOf(iso: string) {
  const monday = weekStart(iso)
  return daysBetween(monday, shiftDate(monday, 6))
}

export function localToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}
