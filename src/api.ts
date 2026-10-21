export type ScheduleItem = {
  id?: string
  date: string
  time: string
  duration: string
  name: string
  type: string
  status: string
  statusCode: AppointmentStatus
  tone: string
  practitionerId: string
}

export type PatientItem = {
  id: string
  name: string
  birth: string
  birthDate: string
  phone: string
  consent: string
}

export type AppointmentStatus = 'BOOKED' | 'CONFIRMED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED'

export type ApiRole = 'ADMINISTRATOR' | 'DOCTOR' | 'MANAGER'

export type Practitioner = {
  id: string
  fullName: string
  specialty: string
  workingHours: Array<{ dayOfWeek: number; startsAt: string; endsAt: string }>
}

export type StatRow = { date: string; practitionerId: string; status: AppointmentStatus; count: number }

export type AuditItem = {
  id: string
  occurredAt: string
  actorId: string
  action: string
  objectType: string
  objectId: string
  result: string
  reason: string | null
  correlationId: string | null
}

const API = import.meta.env.VITE_API_URL ?? 'http://127.0.0.1:8081/api/v1'

export const PETROV_ID = '00000000-0000-0000-0000-000000000201'
export const LEBEDEVA_ID = '00000000-0000-0000-0000-000000000202'

/** Демо-сотрудники: от их имени уходят запросы, чтобы журнал аудита показывал реального автора действия. */
export const ACTORS: Record<ApiRole, { id: string; name: string }> = {
  ADMINISTRATOR: { id: '00000000-0000-0000-0000-000000000901', name: 'Елена Смирнова' },
  DOCTOR: { id: PETROV_ID, name: 'Александр Петров' },
  MANAGER: { id: '00000000-0000-0000-0000-000000000902', name: 'Игорь Кузнецов' },
}

export const demoPractitioners: Practitioner[] = [
  { id: PETROV_ID, fullName: 'Александр Петров', specialty: 'Психиатр', workingHours: [{ dayOfWeek: 2, startsAt: '09:00:00', endsAt: '18:00:00' }, { dayOfWeek: 4, startsAt: '09:00:00', endsAt: '18:00:00' }] },
  { id: LEBEDEVA_ID, fullName: 'Ольга Лебедева', specialty: 'Психотерапевт', workingHours: [{ dayOfWeek: 2, startsAt: '10:00:00', endsAt: '19:00:00' }, { dayOfWeek: 4, startsAt: '10:00:00', endsAt: '19:00:00' }] },
]

function headersFor(role: ApiRole = 'DOCTOR') {
  return { 'Content-Type': 'application/json', 'X-Demo-Role': role, 'X-Actor-Id': ACTORS[role].id }
}

const names: Record<string, string> = {
  '00000000-0000-0000-0000-000000000101': 'Анна Михайлова',
  '00000000-0000-0000-0000-000000000102': 'Дмитрий Волков',
  '00000000-0000-0000-0000-000000000103': 'Мария Соколова',
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(message: string, status: number, code: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

async function failure(response: Response, fallback: string, byStatus: Record<number, string> = {}) {
  let message = fallback
  let code = 'ERROR'
  try {
    const body = await response.json()
    message = body.message || message
    code = body.code || code
  } catch {
    message = byStatus[response.status] ?? message
  }
  return new ApiError(message, response.status, code)
}

export function rememberPatient(id: string, name: string) {
  if (id && name) names[id] = name
}

export async function loadSchedule(date: string, patientId?: string): Promise<ScheduleItem[]> {
  const query = new URLSearchParams({ date })
  if (patientId) query.set('patientId', patientId)
  const response = await fetch(`${API}/appointments?${query}`, { headers: headersFor() })
  if (!response.ok) throw new Error('schedule unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, string>) => toScheduleItem(row, date))
}

export function toneFor(status: AppointmentStatus) {
  return ({
    ARRIVED: 'green', IN_PROGRESS: 'green', COMPLETED: 'done', CONFIRMED: 'blue', BOOKED: 'orange', NO_SHOW: 'red', CANCELLED: 'muted',
  } as Record<string, string>)[status] ?? 'blue'
}

export function statusLabel(status: string) {
  return ({
    BOOKED: 'Ожидает', CONFIRMED: 'Подтверждён', ARRIVED: 'Прибыл', IN_PROGRESS: 'На приёме', COMPLETED: 'Завершён', NO_SHOW: 'Неявка', CANCELLED: 'Отменена',
  } as Record<string, string>)[status] ?? status
}

function toScheduleItem(row: Record<string, string>, date: string): ScheduleItem {
  const start = new Date(row.startsAt)
  const end = new Date(row.endsAt)
  const minutes = Math.round((end.getTime() - start.getTime()) / 60000)
  const statusCode = row.status as AppointmentStatus
  return {
    id: row.id,
    date,
    time: start.toISOString().slice(11, 16),
    duration: `${minutes} мин`,
    name: names[row.patientId] ?? 'Пациент',
    type: row.serviceName,
    status: statusLabel(statusCode),
    statusCode,
    tone: toneFor(statusCode),
    practitionerId: row.practitionerId ?? PETROV_ID,
  }
}

export async function loadPractitioners(): Promise<Practitioner[]> {
  const response = await fetch(`${API}/practitioners`, { headers: headersFor() })
  if (!response.ok) throw new Error('practitioners unavailable')
  const rows = (await response.json()) as Practitioner[]
  if (!Array.isArray(rows) || rows.length === 0) throw new Error('practitioners empty')
  return rows
}

export async function loadPatients(): Promise<PatientItem[]> {
  const response = await fetch(`${API}/patients`, { headers: headersFor() })
  if (!response.ok) throw new Error('patients unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, unknown>) => {
    const patient = toPatient(row)
    rememberPatient(patient.id, patient.name)
    return patient
  })
}

export async function createPatient(input: { fullName: string; birthDate: string; phone: string }): Promise<PatientItem> {
  const response = await fetch(`${API}/patients`, {
    method: 'POST',
    headers: headersFor('ADMINISTRATOR'),
    body: JSON.stringify({ ...input, consentVersion: 'v1.2', consentBasis: 'SIGNED_FORM' }),
  })
  if (!response.ok) throw new Error('patient create failed')
  const patient = toPatient(await response.json())
  rememberPatient(patient.id, patient.name)
  return patient
}

export async function updatePatient(id: string, input: { fullName: string; birthDate: string; phone: string }): Promise<PatientItem> {
  const response = await fetch(`${API}/patients/${id}`, {
    method: 'PUT',
    headers: headersFor('ADMINISTRATOR'),
    body: JSON.stringify(input),
  })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить изменения', { 404: 'Пациент не найден' })
  const patient = toPatient(await response.json())
  rememberPatient(patient.id, patient.name)
  return patient
}

function toPatient(row: Record<string, unknown>): PatientItem {
  const contacts = row.contacts as Array<{ type: string; value: string }>
  const consents = row.consents as Array<{ revokedAt?: string }>
  return {
    id: row.id as string,
    name: row.fullName as string,
    birth: formatDate(row.birthDate as string),
    birthDate: row.birthDate as string,
    phone: contacts.find((contact) => contact.type === 'PHONE')?.value ?? '—',
    consent: consents.some((consent) => !consent.revokedAt) ? 'Действует' : 'Требует обновления',
  }
}

export async function createAppointment(input: {
  patientId: string
  patientName: string
  practitionerId: string
  date: string
  time: string
  minutes: number
  serviceName: string
}, role: ApiRole = 'DOCTOR') {
  rememberPatient(input.patientId, input.patientName)
  const start = new Date(`${input.date}T${input.time}:00Z`)
  if (Number.isNaN(start.getTime())) throw new ApiError('Некорректные дата или время', 400, 'INVALID_INTERVAL')
  const end = new Date(start.getTime() + input.minutes * 60_000)
  const response = await fetch(`${API}/appointments`, {
    method: 'POST',
    headers: headersFor(role),
    body: JSON.stringify({
      patientId: input.patientId,
      practitionerId: input.practitionerId,
      startsAt: start.toISOString(),
      endsAt: end.toISOString(),
      serviceName: input.serviceName,
    }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось создать запись', { 409: 'Интервал врача уже занят' })
  return response.json()
}

export async function cancelAppointment(id: string) {
  const response = await fetch(`${API}/appointments/${id}/cancel`, {
    method: 'POST',
    headers: headersFor('ADMINISTRATOR'),
  })
  if (!response.ok) throw await failure(response, 'Не удалось отменить запись', { 404: 'Запись не найдена' })
  return response.json()
}

export async function changeAppointmentStatus(id: string, status: AppointmentStatus, role: ApiRole) {
  const response = await fetch(`${API}/appointments/${id}/status`, {
    method: 'POST',
    headers: headersFor(role),
    body: JSON.stringify({ status }),
  })
  if (!response.ok) throw await failure(response, 'Не удалось изменить статус', { 404: 'Запись не найдена' })
  return response.json()
}

export async function loadAppointmentStats(from: string, to: string, role: ApiRole = 'MANAGER'): Promise<StatRow[]> {
  const query = new URLSearchParams({ from, to })
  const response = await fetch(`${API}/analytics/appointments?${query}`, { headers: headersFor(role) })
  if (!response.ok) throw await failure(response, 'Аналитика недоступна')
  const body = await response.json()
  return body.rows as StatRow[]
}

export async function loadAudit(role: ApiRole = 'MANAGER', result?: string): Promise<AuditItem[]> {
  const query = new URLSearchParams()
  if (result) query.set('result', result)
  const response = await fetch(`${API}/audit?${query}`, { headers: headersFor(role) })
  if (!response.ok) throw await failure(response, 'Журнал аудита недоступен')
  return response.json()
}

export async function signDemoVisit(diagnosis: string, recommendations: string) {
  const headers = headersFor('DOCTOR')
  const encounterResponse = await fetch(`${API}/clinical/encounters`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      appointmentId: '00000000-0000-0000-0000-000000000301',
      patientId: '00000000-0000-0000-0000-000000000101',
      practitionerId: PETROV_ID,
    }),
  })
  if (!encounterResponse.ok) throw new Error('encounter failed')
  const encounter = await encounterResponse.json()
  const note = { complaints: 'Повышенная тревожность и нарушение сна', mentalStatus: 'Контакт продуктивный', diagnosis, recommendations }
  await fetch(`${API}/clinical/encounters/${encounter.encounterId}/note`, {
    method: 'PUT', headers, body: JSON.stringify(note),
  })
  const documentResponse = await fetch(`${API}/clinical/encounters/${encounter.encounterId}/documents`, {
    method: 'POST', headers, body: JSON.stringify({ templateVersion: 'v2.4', canonicalContent: JSON.stringify(note) }),
  })
  if (!documentResponse.ok) throw new Error('document failed')
  const document = await documentResponse.json()
  const signResponse = await fetch(`${API}/clinical/documents/${document.id}/sign`, { method: 'POST', headers })
  if (!signResponse.ok) throw new Error('sign failed')
  return signResponse.json()
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}
