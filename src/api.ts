export type ScheduleItem = {
  id?: string
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

const names: Record<string, string> = {
  '00000000-0000-0000-0000-000000000101': 'Анна Михайлова',
  '00000000-0000-0000-0000-000000000102': 'Дмитрий Волков',
  '00000000-0000-0000-0000-000000000103': 'Мария Соколова',
}

export async function loadSchedule(): Promise<ScheduleItem[]> {
  const response = await fetch(`${API}/appointments?date=2026-10-20`, { headers })
  if (!response.ok) throw new Error('schedule unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, string>) => {
    const start = new Date(row.startsAt)
    const end = new Date(row.endsAt)
    const minutes = Math.round((end.getTime() - start.getTime()) / 60000)
    return {
      id: row.id,
      time: start.toISOString().slice(11, 16),
      duration: `${minutes} мин`,
      name: names[row.patientId] ?? 'Пациент',
      type: row.serviceName,
      status: statusLabel(row.status),
      tone: row.status === 'ARRIVED' ? 'green' : 'blue',
    }
  })
}

export async function loadPatients(): Promise<PatientItem[]> {
  const response = await fetch(`${API}/patients`, { headers })
  if (!response.ok) throw new Error('patients unavailable')
  const rows = await response.json()
  return rows.map((row: Record<string, unknown>) => {
    const contacts = row.contacts as Array<{ type: string; value: string }>
    const consents = row.consents as Array<{ revokedAt?: string }>
    return {
      id: row.id as string,
      name: row.fullName as string,
      birth: formatDate(row.birthDate as string),
      phone: contacts.find((contact) => contact.type === 'PHONE')?.value ?? '—',
      consent: consents.some((consent) => !consent.revokedAt) ? 'Действует' : 'Требует обновления',
    }
  })
}

export async function createDemoBooking() {
  const response = await fetch(`${API}/appointments`, {
    method: 'POST',
    headers,
    body: JSON.stringify({
      patientId: '00000000-0000-0000-0000-000000000103',
      practitionerId: '00000000-0000-0000-0000-000000000201',
      startsAt: '2026-10-20T13:00:00Z',
      endsAt: '2026-10-20T13:30:00Z',
      serviceName: 'Повторный приём',
    }),
  })
  if (!response.ok) throw new Error('booking failed')
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
  return ({ ARRIVED: 'Прибыл', CONFIRMED: 'Подтверждён', BOOKED: 'Ожидает' } as Record<string, string>)[status] ?? status
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}.${month}.${year}`
}
