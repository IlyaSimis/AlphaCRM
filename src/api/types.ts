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
  /** Снимок цены услуги на момент записи, в копейках; при свободной цене пуст. */
  amountMinor?: number | null
  currency?: string | null
  /** FIXED — фиксированная цена, OPEN — свободная: сумму вводят в кассе. */
  priceMode?: PriceMode | null
  /** Предварительная оценка свободной цены, в копейках. */
  estimateMinor?: number | null
}

export type PriceMode = 'FIXED' | 'OPEN'

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
  /** Есть контакт Telegram (chat id). Сам chat id сервер на экран не отдаёт. */
  telegramConnected?: boolean
  email?: string | null
  /** Второй телефон — отдельный контакт, основной телефон — phone. */
  secondPhone?: string | null
  /** Постоянный администратор; active: false — учётная запись отключена, связь осталась. */
  assignedStaff?: AssignedStaff | null
}

export type AssignedStaff = { id: string; fullName: string; active: boolean }

/** PDF пациента. Байтов здесь нет: файл скачивается отдельным запросом. */
export type PatientFileItem = {
  id: string
  patientId: string
  patientName?: string | null
  appointmentId?: string | null
  originalName: string
  sizeBytes: number
  state: 'DRAFT' | 'SIGNED_LOCKED'
  contentHash?: string | null
  uploadedBy: string
  uploadedByName?: string | null
  uploadedAt: string
  signedBy?: string | null
  signedByName?: string | null
  signedAt?: string | null
}

/** Поля регистратуры. telegramChatId: пусто — не менять; removeTelegram — отключить Telegram. */
export type RegistryInput = { referralSource: string | null; registryNote: string | null; telegramChatId?: string | null; removeTelegram?: boolean }

/** Состояние согласия пациента; надпись на экране — consentLabels. */
export type ConsentState = 'ACTIVE' | 'NEEDS_UPDATE'

export type StaffRole = 'OWNER' | 'SENIOR_ADMINISTRATOR' | 'ADMINISTRATOR' | 'MANAGER' | 'DOCTOR' | 'NURSE' | 'CASHIER' | 'SUPPORT'

/** Сотрудник без хеша пароля. mustChangePassword — вошёл с временным паролем. */
export type StaffItem = {
  id: string
  fullName: string
  username: string
  /** Основная роль; у врача — DOCTOR. */
  role: StaffRole
  /** Все роли сотрудника: права складываются (раздел C). */
  roles?: StaffRole[]
  practitionerId?: string | null
  active: boolean
  mustChangePassword?: boolean
  clinicName?: string | null
  /** Часовой пояс клиники (IANA), в нём интерфейс показывает время. */
  timeZone?: string | null
  /** Коды разрешений роли (раздел C целевой архитектуры): по ним интерфейс прячет кнопки. */
  permissions?: string[]
  permissionsVersion?: string | null
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

/**
 * Услуга справочника. Выключенная (active: false) не предлагается в новой записи. Свободная цена
 * (priceMode: OPEN) — без priceMinor, с пределами minMinor–maxMinor.
 */
export type ServiceItem = { id: string; name: string; durationMinutes: number; priceMinor: number | null; currency: string; active: boolean; priceMode?: PriceMode; minMinor?: number | null; maxMinor?: number | null }

/** Норма расхода услуги: позиция склада и количество на один приём. */
export type ServiceNorm = { itemId: string; quantity: number }

export type TerminalPaymentMethod = 'CARD' | 'SBP'

export type ChargeItem = {
  chargeId: string
  appointmentId: string
  /** Пусто, пока сумма свободной цены не введена (status: AWAITING_AMOUNT). */
  amountMinor: number | null
  currency: string
  status: 'AWAITING_AMOUNT' | 'PENDING' | 'PAID' | 'VOIDED' | 'REFUNDED' | string
  createdAt: string
  paymentId?: string | null
  paymentStatus?: 'PENDING' | 'SUCCEEDED' | 'CANCELLED' | 'FAILED' | 'TIMEOUT' | 'MOCK_NO_PROVIDER' | string | null
  paymentMethod?: 'CARD' | 'SBP' | 'MANUAL' | string | null
  failureReason?: string | null
  priceMode?: PriceMode
  minMinor?: number | null
  maxMinor?: number | null
  estimateMinor?: number | null
  paidAt?: string | null
  /** Аннулирование: код причины и время. */
  voidReason?: string | null
  voidedAt?: string | null
  /** Возврат оплаты: код причины, статус чека возврата, время. */
  refundId?: string | null
  refundReason?: string | null
  refundFiscalStatus?: string | null
  refundedAt?: string | null
}

export type VoidReason = 'DUPLICATE' | 'ERROR' | 'PATIENT_DECLINED' | 'OTHER'
export type RefundReason = 'PATIENT_REQUEST' | 'SERVICE_NOT_PROVIDED' | 'ERROR' | 'OTHER'

export type SalesGroup = 'day' | 'service' | 'practitioner'
export type SalesLine = { key: string; label: string; payments: number; amountMinor: number; refunds: number; refundedMinor: number; netMinor: number; openPriceMinor: number }
export type SalesReport = {
  from: string
  to: string
  groupBy: SalesGroup
  currency: string
  lines: SalesLine[]
  totals: Omit<SalesLine, 'key' | 'label'>
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

export type BreakGlassReason = 'PATIENT_COMPLAINT' | 'LEGAL_REQUEST' | 'DOCTOR_UNAVAILABLE' | 'QUALITY_REVIEW' | 'OTHER'

/** Экстренный доступ владельца к истории пациента; текст причины — только в журнале доступов. */
export type BreakGlassGrant = {
  grantId: string | null
  patientId: string
  patientName?: string | null
  ownerId?: string | null
  ownerName?: string | null
  reasonCode?: BreakGlassReason | null
  reasonText?: string | null
  grantedAt?: string | null
  expiresAt?: string | null
  active: boolean
}

export type Practitioner = {
  id: string
  fullName: string
  specialty: string
  /** Кабинет — подпись на экране; нет — подписи нет. */
  room?: string | null
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

/**
 * Внешний вывод из outbox: шаблон, запись или платёж, канал, статус и время. Текста сообщения и
 * контакта в ответе нет.
 */
export type OutboxItem = {
  id: string
  topic: 'NOTIFICATION' | 'FISCALIZATION' | string
  template: string
  aggregateType: 'APPOINTMENT' | 'PAYMENT' | string
  aggregateId: string
  appointmentStartsAt: string | null
  channel: string | null
  status: 'PENDING' | 'SENDING' | 'SENT' | 'FAILED' | 'CANCELLED' | 'SKIPPED' | string
  reason: string | null
  attempts: number
  createdAt: string
  lastAttemptAt: string | null
  nextAttemptAt: string | null
  sentAt: string | null
}

/** Виджет персонального дашборда: подпись выбирает интерфейс по code. */
export type DashboardWidget = { code: string; scope: 'OWN' | 'CLINIC'; value: number; secondary: number | null; unit: 'count' | 'money' | 'bytes'; items: string[] }

export type Dashboard = { role: string | null; monthStart: string; today: string; generatedAt: string; widgets: DashboardWidget[] }

export type ItemKind = 'DRUG' | 'MEDICAL_DEVICE' | 'CONSUMABLE' | 'GOODS'
export type MarkingSystem = 'NONE' | 'MDLP' | 'GIS_MT'

export type InventoryCategory = { id: string; name: string; parentId: string | null; kind: ItemKind }

export type InventoryItem = {
  id: string
  categoryId: string
  categoryName: string
  kind: ItemKind | null
  name: string
  unit: string
  gtin: string | null
  marked: boolean
  markingSystem: MarkingSystem
  rxOnly: boolean
  active: boolean
  stock: number
}

export type Warehouse = { id: string; name: string; isDefault: boolean }

export type StockDocumentType = 'RECEIPT' | 'CONSUMPTION' | 'WRITE_OFF' | 'TRANSFER' | 'RETURN_TO_SUPPLIER'

export type StockLine = { id?: string; itemId: string; itemName?: string; unit?: string; marked?: boolean; quantity: number; batch: string; expiresOn: string | null; codes: string[] }

export type StockDocument = {
  id: string
  type: StockDocumentType
  status: 'DRAFT' | 'POSTED' | 'CANCELLED'
  documentDate: string
  supplier: string | null
  appointmentId: string | null
  fromWarehouseId: string | null
  toWarehouseId: string | null
  note: string | null
  createdAt: string
  postedAt: string | null
  lines: StockLine[]
  shortages: Array<{ itemId: string; itemName: string; missing: number }>
}

export type StockBalance = {
  itemId: string
  itemName: string
  categoryId: string
  categoryName: string
  kind: ItemKind
  unit: string
  marked: boolean
  warehouseId: string
  warehouseName: string
  batch: string
  expiresOn: string | null
  quantity: number
  codesInStock: number
}

export type ScannedCode = { sgtin: string; gtin: string; serial: string; batch: string; expiresOn: string | null; itemId: string | null; itemName: string | null; codeStatus: string | null; warehouseId: string | null }

export type MarkingCodeItem = { sgtin: string; itemId: string; itemName: string; batch: string; expiresOn: string | null; status: string; warehouseId: string | null; updatedAt: string }

export type MarkingOperationItem = { id: string; type: string; status: 'PENDING' | 'DONE' | 'ERROR'; codes: number; attempts: number; operatorRef: string | null; lastError: string | null; documentId: string; createdAt: string; updatedAt: string }

export type MarkingStatus = { adapter: string; pending: number; errors: number }

export type DrivePermission = 'VIEW' | 'EDIT' | 'MANAGE'

export type DriveNode = {
  id: string
  parentId: string | null
  kind: 'FILE' | 'FOLDER'
  name: string
  ownerId: string
  ownerName: string | null
  sizeBytes: number
  currentVersion: number
  createdAt: string
  updatedAt: string
  deletedAt: string | null
  permission: DrivePermission | null
  shared: boolean
}

export type DriveListing = { node: DriveNode | null; path: Array<{ id: string; name: string }>; children: DriveNode[] }

export type DriveVersion = { version: number; sizeBytes: number; sha256: string; contentType: string | null; uploadedBy: string; uploadedByName: string | null; uploadedAt: string }

export type DriveShare = { id: string; principalType: 'USER' | 'ROLE' | 'CLINIC' | 'LINK'; principalId: string | null; label: string | null; permission: DrivePermission; expiresAt: string | null; createdAt: string; token: string | null }

export type DriveQuota = { bytesUsed: number; bytesLimit: number; maxFileBytes: number }

export type DrivePrincipal = { id: string; fullName: string; role: StaffRole }

/** Личная запись рабочего пространства. Тип и статус — свободные строки, а не закрытый справочник. */
export type WorkspaceField = { name: string; value: string }
export type ChecklistItem = { text: string; done: boolean }
export type WorkspaceItemInput = {
  title: string
  type: string
  body: string
  status: string
  color: 'green' | 'blue' | 'orange' | 'red' | 'purple' | 'gray'
  completed: boolean
  dueAt: string | null
  remindAt: string | null
  patientId: string | null
  appointmentId: string | null
  tags: string[]
  fields: WorkspaceField[]
  checklist: ChecklistItem[]
}
export type WorkspaceItem = WorkspaceItemInput & {
  id: string
  createdAt: string
  updatedAt: string
  completedAt: string | null
  archivedAt: string | null
}
export type WorkspaceDueFilter = 'all' | 'today' | 'overdue' | 'week' | 'none'
export type WorkspaceFilter = {
  query: string
  type: string
  status: string
  tag: string
  due: WorkspaceDueFilter
  sort: 'due' | 'updated' | 'created' | 'title'
  display: 'list' | 'board'
}
export type WorkspaceView = { id: string; name: string; filter: WorkspaceFilter; createdAt: string }
