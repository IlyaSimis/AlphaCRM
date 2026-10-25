/**
 * Время клиники. Сервер хранит моменты (UTC), а дни, смены и время на экране — в часовом поясе
 * клиники (CLINIC_TIME_ZONE; сервер присылает его в /auth/me). По умолчанию — UTC, как раньше.
 */
let zone = 'UTC'

function valid(timeZone: string) {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone })
    return true
  } catch {
    return false
  }
}

export function setClinicTimeZone(timeZone?: string | null) {
  zone = timeZone && valid(timeZone) ? timeZone : 'UTC'
}

export function clinicTimeZone() {
  return zone
}

type Parts = { year: number; month: number; day: number; hour: number; minute: number }

function partsOf(instant: number): Parts {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(instant))
  const get = (type: string) => Number(parts.find((part) => part.type === type)?.value)
  return { year: get('year'), month: get('month'), day: get('day'), hour: get('hour') % 24, minute: get('minute') }
}

const pad = (value: number) => String(value).padStart(2, '0')

/** Дата `YYYY-MM-DD` и время `HH:mm` момента в поясе клиники. */
export function clinicParts(iso: string | Date): { date: string; time: string } {
  const instant = typeof iso === 'string' ? new Date(iso).getTime() : iso.getTime()
  const p = partsOf(instant)
  return { date: `${p.year}-${pad(p.month)}-${pad(p.day)}`, time: `${pad(p.hour)}:${pad(p.minute)}` }
}

/** Смещение пояса клиники от UTC в момент instant, мс. */
function offsetAt(instant: number) {
  const p = partsOf(instant)
  return Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute) - Math.floor(instant / 60_000) * 60_000
}

/** Момент для даты и времени на часах клиники. NaN-дата — если строка некорректна. */
export function clinicInstant(date: string, time: string): Date {
  const [year, month, day] = date.split('-').map(Number)
  const [hour, minute] = time.split(':').map(Number)
  const wall = Date.UTC(year, month - 1, day, hour, minute)
  if (Number.isNaN(wall)) return new Date(Number.NaN)
  // Две итерации — на случай перехода на летнее время между догадкой и ответом.
  let instant = wall - offsetAt(wall)
  instant = wall - offsetAt(instant)
  return new Date(instant)
}

/** Сегодняшняя дата на часах клиники. */
export function clinicToday() {
  return clinicParts(new Date()).date
}

/** «25.10, 17:17» — момент на часах клиники (журнал, внешние выводы). */
export function formatClinicMoment(iso: string | null | undefined, withSeconds = false) {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString('ru-RU', { timeZone: zone, day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', ...(withSeconds ? { second: '2-digit' } : {}) })
}
