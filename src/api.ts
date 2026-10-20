export type ScheduleItem = {
  id?: string
  date: string
  time: string
  duration: string
  name: string
  type: string
  status: string
  tone: string
}

export type PatientItem = {
  id: string
  name: string
  birth: string
  phone: string
  consent: string
}

const API = import.meta.env.VITE_API_URL ?? 'http://127.0.0.1:8081/api/v1'
const headers = { 'Content-Type': 'application/json', 'X-Demo-Role': 'DOCTOR' }

const PETROV_ID = '00000000-0000-0000-0000-000000000201'

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

export function rememberPatient(id: string, name: string) {
  if (id && name) names[id] = name
}

export async function loadSchedule(date: string, patientId?: string): Promise<ScheduleItem[]> {
  const query = new URLSearchParams({ date })
  if (patientId) query.set('patientId', patientId)
  const response = await fetch(`${API}/appointments?${query}`, { headers })
  if (!response.ok) throw new Error('schedule unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, string>) => toScheduleItem(row, date))
}

function toScheduleItem(row: Record<string, string>, date: string): ScheduleItem {
  const start = new Date(row.startsAt)
  const end = new Date(row.endsAt)
  const minutes = Math.round((end.getTime() - start.getTime()) / 60000)
  return {
    id: row.id,
    date,
    time: start.toISOString().slice(11, 16),
    duration: `${minutes} мин`,
    name: names[row.patientId] ?? 'Пациент',
    type: row.serviceName,
    status: statusLabel(row.status),
    tone: row.status === 'ARRIVED' ? 'green' : row.status === 'CANCELLED' ? 'muted' : row.status === 'BOOKED' ? 'orange' : 'blue',
  }
}

export async function loadPatients(): Promise<PatientItem[]> {
  const response = await fetch(`${API}/patients`, { headers })
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
    headers: { ...headers, 'X-Demo-Role': 'ADMINISTRATOR' },
    body: JSON.stringify({ ...input, consentVersion: 'v1.2', consentBasis: 'SIGNED_FORM' }),
  })
  if (!response.ok) throw new Error('patient create failed')
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
    phone: contacts.find((contact) => contact.type === 'PHONE')?.value ?? '—',
    consent: consents.some((consent) => !consent.revokedAt) ? 'Действует' : 'Требует обновления',
  }
}

export async function createAppointment(input: {
  patientId: string
  patientName: string
  date: string
  time: string
  minutes: number
  serviceName: string
}) {
  rememberPatient(input.patientId, input.patientName)
  const start = new Date(`${input.date}T${input.time}:00Z`)
  if (Number.isNaN(start.getTime())) throw new ApiError('Некорректные дата или время', 400, 'INVALID_INTERVAL')
  const end = new Date(start.getTime() + input.minutes * 60_000)
  const response = await fetch(`${API}/appointments`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      patientId: input.patientId,
      practitionerId: PETROV_ID,
      startsAt: start.toISOString(),
      endsAt: end.toISOString(),
      serviceName: input.serviceName,
    }),
  })
  if (!response.ok) {
    let message = 'Не удалось создать запись'
    let code = 'ERROR'
    try {
      const body = await response.json()
      message = body.message || message
      code = body.code || code
    } catch {
      message = response.status === 409 ? 'Интервал врача уже занят' : message
    }
    throw new ApiError(message, response.status, code)
  }
  return response.json()
}

export async function cancelAppointment(id: string) {
  const response = await fetch(`${API}/appointments/${id}/cancel`, {
    method: 'POST',
    headers: { ...headers, 'X-Demo-Role': 'ADMINISTRATOR' },
  })
  if (!response.ok) {
    let message = 'Не удалось отменить запись'
    let code = 'ERROR'
    try {
      const body = await response.json()
      message = body.message || message
      code = body.code || code
    } catch {
      message = response.status === 404 ? 'Запись не найдена' : message
    }
    throw new ApiError(message, response.status, code)
  }
  return response.json()
}

export async function signDemoVisit(diagnosis: string, recommendations: string) {
  const encounterResponse = await fetch(`${API}/clinical/encounters`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      appointmentId: '00000000-0000-0000-0000-000000000301',
      patientId: '00000000-0000-0000-0000-000000000101',
      practitionerId: '00000000-0000-0000-0000-000000000201',
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

function statusLabel(status: string) {
  return ({ ARRIVED: 'Прибыл', CONFIRMED: 'Подтверждён', BOOKED: 'Ожидает', CANCELLED: 'Отменена' } as Record<string, string>)[status] ?? status
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}
