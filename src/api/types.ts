export type AppointmentStatus = 'BOOKED' | 'CONFIRMED' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED'

/** Запись в расписании в том виде, в каком её показывает интерфейс. */
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
  patientId: string
  minutes: number
  /** Снимок цены услуги на момент записи, в копейках. */
  amountMinor?: number | null
  currency?: string | null
}

export type PatientItem = {
  id: string
  name: string
  birth: string
  birthDate: string
  phone: string
  consent: string
  consents: ConsentItem[]
}

export type ConsentItem = { id: string; version: string; basis: string; grantedAt: string; revokedAt?: string | null }

export type ServiceItem = { id: string; name: string; durationMinutes: number; priceMinor: number; currency: string }

export type ChargeItem = {
  chargeId: string
  appointmentId: string
  amountMinor: number
  currency: string
  status: 'PENDING' | 'PAID' | string
  createdAt: string
  paymentId?: string | null
  paymentStatus?: string | null
}

export type DocumentState = 'DRAFT' | 'SIGNED_LOCKED'

/** Версия документа приёма без содержимого. */
export type DocumentSummary = { id: string; state: DocumentState; templateVersion: string; signedAt?: string | null; amendsDocumentId?: string | null }

export type NoteFields = { complaints: string; mentalStatus: string; diagnosis: string; recommendations: string }

export type EncounterView = {
  encounterId: string
  appointmentId: string
  patientId: string
  practitionerId: string
  status: string
  startedAt: string
  complaints?: string | null
  mentalStatus?: string | null
  diagnosis?: string | null
  recommendations?: string | null
  noteVersion?: number | null
  documents: DocumentSummary[]
}

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
