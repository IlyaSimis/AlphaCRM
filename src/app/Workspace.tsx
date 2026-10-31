import { useMemo, useState } from 'react'
import { Box, Drawer, Snackbar } from '@mui/material'
import { changePassword } from '../api/auth'
import type { AssignedStaff, EncounterView, PatientItem, ScheduleItem, StaffItem, WaitlistItem } from '../api/types'
import type { NavigationDestination, WorkspaceNavigationIntent } from '../features/assistant/navigationCatalog'
import type { BookingDraft } from '../features/schedule/booking'
import { filterPatients } from '../features/patients/search'
import { phoneSharedWith } from '../features/registry/registry'
import { useRegistry } from '../features/registry/useRegistry'
import type { VisitTarget } from '../features/visit/visitRules'
import { formatDay, localToday } from '../shared/dates'
import { administratorRoles, staffRolesOf } from '../shared/roles'
import { Landscape } from './Landscape'
import { canOpen, firstPageOf, pageTitles, sectionsFor, serviceOf, servicesFor, startPage, type Page, type Service } from './pages'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'
import { useClinicData } from './useClinicData'
import { rolesOf } from '../shared/permissions'
import { WorkspaceContent } from './WorkspaceContent'
import { WorkspaceDialogs } from './WorkspaceDialogs'

type WorkspaceProps = {
  staff: StaffItem
  /** true — вошли на сервер; false — демо-песочница без сервера. */
  live: boolean
  onSignOut: () => void
}

/** Рабочее место сотрудника. Роль и врач берутся из учётной записи — переключить их нельзя. */
export function Workspace({ staff, live, onSignOut }: WorkspaceProps) {
  const role = rolesOf(staff)
  // Для врача автор действия — его карточка врача: тот же id, что actorId() на сервере.
  const doctorId = staff.practitionerId ?? ''
  const clinic = useClinicData(doctorId, live)
  const registry = useRegistry(staff, live, clinic.apiOnline, clinic.notify)
  // Ссылка на файл диска (…?drive-link=…) открывает диск сразу после входа.
  const [page, setPage] = useState<Page>(() => (typeof window !== 'undefined' && new URLSearchParams(window.location.search).has('drive-link') && canOpen('drive', role) ? 'drive' : startPage(role)))
  // Последняя открытая страница каждого сервиса: вкладка сверху возвращает туда, где остановились.
  const [lastPages, setLastPages] = useState<Partial<Record<Service, Page>>>({})
  const [query, setQuery] = useState('')
  const [drawer, setDrawer] = useState(false)
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [doctorFilter, setDoctorFilter] = useState('all')
  const [booking, setBooking] = useState<{ date: string; practitionerId?: string; time?: string } | null>(null)
  const [bookingKey, setBookingKey] = useState(0)
  const [bookingError, setBookingError] = useState('')
  const [pendingBookingPatient, setPendingBookingPatient] = useState<{ draft: BookingDraft; fullName: string; patient?: PatientItem } | null>(null)
  const [moving, setMoving] = useState<ScheduleItem | null>(null)
  const [moveError, setMoveError] = useState('')
  const [patientDialog, setPatientDialog] = useState(false)
  const [patientImport, setPatientImport] = useState(false)
  const [editPatient, setEditPatient] = useState<PatientItem | null>(null)
  const [editError, setEditError] = useState('')
  const [createError, setCreateError] = useState('')
  // Открытые за смену приёмы: переход к другому пациенту не закрывает и не подписывает предыдущий.
  const [visits, setVisits] = useState<VisitTarget[]>([])
  const [visit, setVisit] = useState<VisitTarget | null>(null)
  const [visitKey, setVisitKey] = useState(0)
  const [password, setPassword] = useState(false)
  const [passwordError, setPasswordError] = useState('')
  const [waitSlot, setWaitSlot] = useState<ScheduleItem | null>(null)
  const [waitError, setWaitError] = useState('')
  const [assistant, setAssistant] = useState(false)
  const [settings, setSettings] = useState(false)
  const [workspaceIntent, setWorkspaceIntent] = useState<({ key: number } & WorkspaceNavigationIntent) | null>(null)

  const { patientItems, scheduleItems, practitioners, scheduleDate } = clinic
  const visiblePatients = useMemo(() => filterPatients(patientItems, query), [patientItems, query])
  const selectedPatient = patientItems.find((patient) => patient.id === clinic.selectedPatientId) ?? patientItems[0]

  const navigate = (next: Page) => {
    const target = canOpen(next, role) ? next : startPage(role)
    setPage(target)
    setLastPages((pages) => ({ ...pages, [serviceOf(target)]: target }))
    setDrawer(false)
  }

  const navigateFromAssistant = (destination: NavigationDestination) => {
    if (destination.page === 'workspace') {
      setWorkspaceIntent({ key: Date.now(), ...destination.workspaceIntent })
    }
    navigate(destination.page)
  }

  const service = serviceOf(page)
  const openService = (next: Service) => {
    const remembered = lastPages[next]
    const target = remembered && canOpen(remembered, role) ? remembered : firstPageOf(next, role)
    if (target) navigate(target)
  }

  const openBooking = (date = scheduleDate, practitionerId?: string, time?: string) => {
    setBookingError('')
    setBookingKey((value) => value + 1)
    setBooking({ date, practitionerId, time })
  }

  const openPatient = (patient: PatientItem) => {
    clinic.setSelectedPatientId(patient.id)
    navigate('patient')
  }

  const openPatientById = (id: string) => {
    const match = patientItems.find((patient) => patient.id === id)
    if (match) openPatient(match)
  }

  const openPatientOf = (item: ScheduleItem) => {
    const match = patientItems.find((patient) => (item.patientId ? patient.id === item.patientId : patient.name === item.name)) ?? patientItems.find((patient) => patient.name === item.name)
    if (match) openPatient(match)
  }

  const sameVisit = (left: VisitTarget, right: VisitTarget) =>
    left.appointmentId ? left.appointmentId === right.appointmentId : left.encounter?.encounterId === right.encounter?.encounterId

  const openVisit = (target: VisitTarget) => {
    setVisits((items) => [...items.filter((item) => !sameVisit(item, target)), target])
    setVisit(target)
    setVisitKey((value) => value + 1)
    navigate('visit')
  }

  const startVisit = (item: ScheduleItem) => {
    // В песочнице запись сразу «На приёме»; на сервере статус ставит старт приёма (onStarted).
    if (!clinic.apiOnline) void clinic.visitStarted(item)
    openVisit({
      patientId: item.patientId,
      patientName: item.name,
      practitionerId: item.practitionerId,
      appointmentId: item.id,
      date: item.date,
      time: item.time,
      minutes: item.minutes,
      serviceName: item.type,
    })
  }

  const visitAppointment = visit?.appointmentId ? scheduleItems.find((item) => item.id === visit.appointmentId) : undefined
  // Врач в песочнице ведёт записи демо-врача: в документе — его имя.
  const doctorName = practitioners.find((item) => item.id === doctorId)?.fullName ?? staff.fullName

  const openEncounter = (patient: PatientItem, encounter: EncounterView) => {
    const appointment = scheduleItems.find((item) => item.id === encounter.appointmentId)
    openVisit({
      patientId: patient.id,
      patientName: patient.name,
      practitionerId: encounter.practitionerId,
      appointmentId: encounter.appointmentId,
      date: appointment?.date ?? encounter.startedAt.slice(0, 10),
      time: appointment?.time,
      minutes: appointment?.minutes,
      serviceName: appointment?.type,
      encounter,
    })
  }

  /** Освободившийся слот занимаем обычной записью, потом закрываем ожидание ссылкой на неё. */
  const bookWaiting = async (slot: ScheduleItem, entry: WaitlistItem) => {
    setWaitError('')
    const result = await clinic.book({ patientId: entry.patientId, practitionerId: slot.practitionerId, serviceId: entry.serviceId, date: slot.date, time: slot.time })
    if (result.status === 'error') {
      setWaitError(result.message)
      return
    }
    if (result.status === 'done') {
      if (result.id) await registry.markBooked(entry, result.id)
      setWaitSlot(null)
    }
  }

  // Постоянный администратор выбирается из активных администраторов клиники.
  const administrators = registry.staffList.filter((item) => staffRolesOf(item).some((itemRole) => administratorRoles.includes(itemRole)))
  const assignedOf = (id: string | null, current?: AssignedStaff | null): AssignedStaff | null => {
    if (!id) return null
    if (current?.id === id) return current
    const match = administrators.find((item) => item.id === id)
    return match ? { id: match.id, fullName: match.fullName, active: match.active } : null
  }

  const sharedPhone = (patient: PatientItem) => clinic.apiOnline ? patient.phoneSharedWith ?? [] : phoneSharedWith([patient.phone, patient.secondPhone], patientItems, patient.id)

  const saveBooking = async (draft: Parameters<typeof clinic.book>[0]) => {
    const result = await clinic.book(draft)
    if (result.status === 'error') setBookingError(result.message)
    if (result.status === 'done') {
      setBooking(null)
      setBookingError('')
    }
  }

  const prepareBookingPatient = (draft: BookingDraft, fullName: string) => {
    setBooking(null)
    setBookingError('')
    setCreateError('')
    setPendingBookingPatient({ draft, fullName })
  }

  /** Сначала полная карточка проходит серверные проверки, затем создаётся связанная запись. */
  const saveBookingPatient = async (input: Parameters<typeof clinic.createPatient>[0]) => {
    const pending = pendingBookingPatient
    if (!pending) return
    let patient = pending.patient
    if (patient) {
      const error = await clinic.updatePatient(patient, input, assignedOf(input.assignedStaffId, patient.assignedStaff))
      if (error) {
        setCreateError(error)
        return
      }
      patient = { ...patient, name: input.fullName }
    } else {
      const error = await clinic.createPatient(input, assignedOf(input.assignedStaffId), (created) => { patient = created })
      if (error || !patient) {
        setCreateError(error ?? 'Не удалось сохранить пациента')
        return
      }
    }
    const result = await clinic.book({ ...pending.draft, patientId: patient.id }, patient)
    if (result.status === 'error') {
      setPendingBookingPatient({ ...pending, patient })
      setCreateError(`Карточка пациента сохранена, но запись не создана: ${result.message}`)
      return
    }
    if (result.status === 'done') {
      setPendingBookingPatient(null)
      setCreateError('')
    }
  }

  const saveMoving = async (draft: Parameters<typeof clinic.reschedule>[1]) => {
    if (!moving) return
    const result = await clinic.reschedule(moving, draft)
    if (result.status === 'error') setMoveError(result.message)
    if (result.status === 'done') setMoving(null)
  }

  const createPatient = async (input: Parameters<typeof clinic.createPatient>[0]) => {
    const error = await clinic.createPatient(input, assignedOf(input.assignedStaffId))
    if (error) {
      setCreateError(error)
      return
    }
    setPatientDialog(false)
    setCreateError('')
  }

  const updatePatient = async (input: Parameters<typeof clinic.updatePatient>[1]) => {
    if (!editPatient) return
    const error = await clinic.updatePatient(editPatient, input, assignedOf(input.assignedStaffId, editPatient.assignedStaff))
    if (error) {
      setEditError(error)
      return
    }
    setEditPatient(null)
    setEditError('')
  }

  const savePassword = async (input: { currentPassword: string; newPassword: string }) => {
    try {
      await changePassword(input.currentPassword, input.newPassword)
      setPassword(false)
      clinic.notify('Пароль изменён')
    } catch (error) {
      setPasswordError((error as Error).message)
    }
  }

  const eyebrow = page === 'today'
    ? `РАБОЧИЙ ДЕНЬ · ${formatDay(localToday()).toLocaleUpperCase('ru-RU')}`
    : page === 'visit'
    ? 'КЛИНИЧЕСКИЙ КОНТУР · ДОСТУП ПО СВЯЗИ С ЛЕЧЕНИЕМ'
    : page === 'documents' ? 'КЛИНИЧЕСКИЙ АРХИВ · ТОЛЬКО ЧТЕНИЕ'
    : service === 'inventory' ? 'СКЛАД И МАРКИРОВКА'
    : service === 'drive' ? 'КОРПОРАТИВНЫЙ ДИСК'
    : service === 'finance' ? 'ФИНАНСЫ'
    : service === 'management' ? 'УПРАВЛЕНИЕ КЛИНИКОЙ'
    : service === 'dashboards' ? 'РАБОЧЕЕ ПРОСТРАНСТВО'
    : page === 'registry' ? 'АДМИНИСТРАТИВНЫЙ КОНТУР' : page === 'hours' ? 'НАСТРОЙКИ РАСПИСАНИЯ' : page === 'patients' || page === 'patient' ? 'КАРТОТЕКА ПАЦИЕНТОВ' : formatDay(scheduleDate).toLocaleUpperCase('ru-RU')

  return (
    <Box className="app-shell">
      <Box className={`desktop-sidebar ${sidebarCollapsed ? 'collapsed' : ''}`}><Sidebar className="sidebar" page={page} service={service} services={servicesFor(role)} staff={staff} live={live} visits={visits} activeVisit={page === 'visit' ? visit : null} collapsed={sidebarCollapsed} onService={openService} onNavigate={navigate} onVisit={openVisit} onSettings={() => setSettings(true)} onToggleCollapse={() => setSidebarCollapsed((value) => !value)} /></Box>
      <Drawer open={drawer} onClose={() => setDrawer(false)}><Sidebar className="sidebar sidebar-in-drawer" page={page} service={service} services={servicesFor(role)} staff={staff} live={live} visits={visits} activeVisit={page === 'visit' ? visit : null} onService={openService} onNavigate={navigate} onVisit={(target) => { openVisit(target); setDrawer(false) }} onSettings={() => { setDrawer(false); setSettings(true) }} /></Drawer>
      <Box component="main" className="main">
        <Landscape />
        <Topbar eyebrow={eyebrow} title={page === 'today' ? '' : pageTitles[page]} pages={sectionsFor(service, role).flatMap((section) => section.pages)} page={page} onNavigate={navigate} live={live} clinicName={staff.clinicName} onAssistant={() => setAssistant(true)} onSignOut={onSignOut} onMenu={() => setDrawer(true)} />
        <Box className="content">
          <WorkspaceContent
            page={page} staff={staff} role={role} doctorId={doctorId} live={live} clinic={clinic} registry={registry}
            query={query} visiblePatients={visiblePatients} selectedPatient={selectedPatient} doctorFilter={doctorFilter}
            workspaceIntent={workspaceIntent} visit={visit} visitKey={visitKey} visitAppointment={visitAppointment} doctorName={doctorName}
            sharedPhone={sharedPhone} onQuery={setQuery} onDoctorFilter={setDoctorFilter} onNavigate={navigate} onOpenBooking={openBooking}
            onNewPatient={() => { setCreateError(''); setPatientDialog(true) }} onCreatePatient={() => setPatientDialog(true)}
            onImportPatients={() => setPatientImport(true)} onEditPatient={(patient, addRepresentative = false) => {
              setEditError('')
              setEditPatient(addRepresentative ? { ...patient, profile: { ...patient.profile, representatives: [...(patient.profile?.representatives ?? []), { fullName: '', relationship: '', primary: false }] } } : patient)
            }}
            onOpenPatient={openPatient} onOpenPatientById={openPatientById} onOpenPatientOf={openPatientOf} onOpenEncounter={openEncounter}
            onStartVisit={startVisit} onReschedule={(item) => { setMoveError(''); setMoving(item) }} onWaitlist={(item) => { setWaitError(''); setWaitSlot(item) }}
          />
        </Box>
      </Box>

      <WorkspaceDialogs
        staff={staff} role={role} live={live} page={page} clinic={clinic} registry={registry} administrators={administrators}
        doctorFilter={doctorFilter} booking={booking} bookingKey={bookingKey} bookingError={bookingError} pendingBookingPatient={pendingBookingPatient}
        moving={moving} moveError={moveError} waitSlot={waitSlot} waitError={waitError}
        patientDialog={patientDialog} createError={createError} patientImport={patientImport}
        editPatient={editPatient} editError={editError} settings={settings} password={password} passwordError={passwordError} assistant={assistant}
        onBookingClose={() => { setBooking(null); setBookingError('') }} onBookingSave={saveBooking} onBookingNewPatient={prepareBookingPatient}
        onBookingPatientClose={() => { setPendingBookingPatient(null); setCreateError('') }} onBookingPatientSave={saveBookingPatient}
        onMovingClose={() => setMoving(null)} onMovingSave={saveMoving}
        onWaitlistClose={() => setWaitSlot(null)} onBookWaiting={(entry) => { if (waitSlot) void bookWaiting(waitSlot, entry) }}
        onPatientCreateClose={() => { setPatientDialog(false); setCreateError('') }} onPatientCreate={createPatient}
        onPatientImportClose={() => setPatientImport(false)}
        onPatientEditClose={() => { setEditPatient(null); setEditError('') }} onPatientEdit={updatePatient}
        onSettingsClose={() => setSettings(false)} onPasswordOpen={() => { setSettings(false); setPasswordError(''); setPassword(true) }}
        onPasswordClose={() => setPassword(false)} onPasswordSave={savePassword}
        onAssistantClose={() => setAssistant(false)} onAssistantNavigate={navigateFromAssistant}
      />
      <Snackbar open={clinic.notice.open} autoHideDuration={2500} onClose={clinic.closeNotice} message={clinic.notice.message} />
    </Box>
  )
}
