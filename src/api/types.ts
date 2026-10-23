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
  consent: ConsentState
  consents: ConsentItem[]
  /** Откуда пришёл пациент — код из справочника регистратуры. */
  referralSource?: string | null
  /** Административная заметка регистратуры: в клинический контур не попадает. */
  registryNote?: string | null
  /** Другие карточки с тем же телефоном. */
  phoneSharedWith?: Array<{ id: string; fullName: string }>
}

/** Состояние согласия пациента; надпись на экране — consentLabels. */
export type ConsentState = 'ACTIVE' | 'NEEDS_UPDATE'

export type StaffRole = 'ADMINISTRATOR' | 'DOCTOR' | 'MANAGER' | 'CASHIER'

/** Сотрудник без хеша пароля. mustChangePassword — вошёл с временным паролем. */
export type StaffItem = {
  id: string
  fullName: string
  username: string
  role: StaffRole
  practitionerId?: string | null
  active: boolean
  mustChangePassword?: boolean
  clinicName?: string | null
}

/** Подписанный документ из архива: текст — из снимка подписи, а не из текущей заметки. */
export type SignedDocumentItem = {
  id: string
  encounterId: string
  appointmentId: string
  patientId: string
  patientName?: string | null
  practitionerId: string
  practitionerName?: string | null
  signedAt: string
  templateVersion: string
  amendsDocumentId?: string | null
  contentHash?: string | null
  complaints?: string | null
  mentalStatus?: string | null
  diagnosis?: string | null
  recommendations?: string | null
}


/** Задача регистратуры: свободный текст; patientId — null, если задача не про пациента. */
export type TaskItem = { id: string; patientId: string | null; title: string; dueDate: string; assigneeId: string; status: 'OPEN' | 'DONE'; createdAt?: string; doneAt?: string | null }

export type WaitlistItem = { id: string; patientId: string; practitionerId: string; serviceId: string; wishFrom: string; wishTo: string; status: 'ACTIVE' | 'BOOKED' | 'CANCELLED'; appointmentId?: string | null }

export type ConsentItem = { id: string; version: string; basis: string; grantedAt: string; revokedAt?: string | null }

/** Услуга справочника. Выключенная (active: false) не предлагается в новой записи. */
export type ServiceItem = { id: string; name: string; durationMinutes: number; priceMinor: number; currency: string; active: boolean }

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
  /** Имя сотрудника своей клиники — подставляет сервер. */
  actorName?: string | null
  action: string
  objectType: string
  objectId: string
  result: string
  reason: string | null
  correlationId: string | null
}
