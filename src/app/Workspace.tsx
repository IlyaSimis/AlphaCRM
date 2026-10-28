import { useMemo, useState } from 'react'
import { Box, Drawer, Snackbar, Typography } from '@mui/material'
import { changePassword } from '../api/auth'
import { PETROV_ID } from '../api/client'
import type { AssignedStaff, EncounterView, PatientItem, ScheduleItem, StaffItem, WaitlistItem } from '../api/types'
import { Analytics } from '../features/analytics/Analytics'
import { AuditLog } from '../features/audit/AuditLog'
import { PasswordDialog } from '../features/auth/PasswordDialog'
import { Cashier } from '../features/cashier/Cashier'
import { SalesPage } from '../features/finance/SalesPage'
import { SignedDocuments } from '../features/documents/SignedDocuments'
import { PatientFiles } from '../features/files/PatientFiles'
import { OutboxPage } from '../features/outbox/OutboxPage'
import { PatientCard } from '../features/patients/PatientCard'
import { PatientDialog } from '../features/patients/PatientDialog'
import { PatientsList } from '../features/patients/PatientsList'
import { RegistryPanel } from '../features/patients/RegistryPanel'
import { filterPatients } from '../features/patients/search'
import { phoneSharedWith } from '../features/registry/registry'
import { RegistryPage } from '../features/registry/RegistryPage'
import { useRegistry } from '../features/registry/useRegistry'
import { WaitlistMatchesDialog } from '../features/registry/WaitlistMatchesDialog'
import { BookingDialog } from '../features/schedule/BookingDialog'
import { RescheduleDialog } from '../features/schedule/RescheduleDialog'
import { Schedule } from '../features/schedule/Schedule'
import { SettingsPage } from '../features/settings/SettingsPage'
import { StaffPage } from '../features/staff/StaffPage'
import { DashboardWidgets } from '../features/today/DashboardWidgets'
import { TodayPage } from '../features/today/TodayPage'
import { InventoryPage } from '../features/inventory/InventoryPage'
import { DrivePage } from '../features/drive/DrivePage'
import { Visit } from '../features/visit/Visit'
import type { VisitTarget } from '../features/visit/visit'
import { formatDay, localToday } from '../shared/dates'
import { greeting } from '../shared/greeting'
import { administratorRoles, canManagePrices, canManageRegistry, canSeeRegistry, canWriteClinical } from '../shared/roles'
import { Landscape } from './Landscape'
import { canOpen, driveView, firstPageOf, pageTitles, serviceOf, servicesFor, startPage, stockSection, type Page, type Service } from './pages'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'
import { useClinicData } from './useClinicData'

type WorkspaceProps = {
  staff: StaffItem
  /** true — вошли на сервер; false — демо-песочница без сервера. */
  live: boolean
  onSignOut: () => void
}

/** Рабочее место сотрудника. Роль и врач берутся из учётной записи — переключить их нельзя. */
export function Workspace({ staff, live, onSignOut }: WorkspaceProps) {
  const role = staff.role
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
  const [doctorFilter, setDoctorFilter] = useState('all')
  const [booking, setBooking] = useState<{ date: string; practitionerId?: string } | null>(null)
  const [bookingKey, setBookingKey] = useState(0)
  const [bookingError, setBookingError] = useState('')
  const [moving, setMoving] = useState<ScheduleItem | null>(null)
  const [moveError, setMoveError] = useState('')
  const [patientDialog, setPatientDialog] = useState(false)
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

  const { patientItems, scheduleItems, practitioners, scheduleDate } = clinic
  const visiblePatients = useMemo(() => filterPatients(patientItems, query), [patientItems, query])
  const selectedPatient = patientItems.find((patient) => patient.id === clinic.selectedPatientId) ?? patientItems[0]

  const navigate = (next: Page) => {
    const target = canOpen(next, role) ? next : startPage(role)
    setPage(target)
    setLastPages((pages) => ({ ...pages, [serviceOf(target)]: target }))
    setDrawer(false)
  }

  const service = serviceOf(page)
  const openService = (next: Service) => {
    const remembered = lastPages[next]
    const target = remembered && canOpen(remembered, role) ? remembered : firstPageOf(next, role)
    if (target) navigate(target)
  }

  const openBooking = (date = scheduleDate, practitionerId?: string) => {
    setBookingError('')
    setBookingKey((value) => value + 1)
    setBooking({ date, practitionerId })
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
  const administrators = registry.staffList.filter((item) => administratorRoles.includes(item.role))
  const assignedOf = (id: string | null, current?: AssignedStaff | null): AssignedStaff | null => {
    if (!id) return null
    if (current?.id === id) return current
    const match = administrators.find((item) => item.id === id)
    return match ? { id: match.id, fullName: match.fullName, active: match.active } : null
  }

  const sharedPhone = (patient: PatientItem) => clinic.apiOnline ? patient.phoneSharedWith ?? [] : phoneSharedWith([patient.phone, patient.secondPhone], patientItems, patient.id)

  const eyebrow = page === 'today'
    ? `РАБОЧИЙ ДЕНЬ · ${formatDay(localToday()).toLocaleUpperCase('ru-RU')}`
    : page === 'visit'
    ? 'КЛИНИЧЕСКИЙ КОНТУР · ДОСТУП ПО СВЯЗИ С ЛЕЧЕНИЕМ'
    : page === 'documents' ? 'КЛИНИЧЕСКИЙ АРХИВ · ТОЛЬКО ЧТЕНИЕ'
    : service === 'inventory' ? 'СКЛАД И МАРКИРОВКА'
    : service === 'drive' ? 'КОРПОРАТИВНЫЙ ДИСК'
    : service === 'finance' ? 'ФИНАНСЫ'
    : service === 'dashboards' ? 'УПРАВЛЕНИЕ КЛИНИКОЙ'
    : page === 'registry' ? 'АДМИНИСТРАТИВНЫЙ КОНТУР' : page === 'hours' ? 'НАСТРОЙКИ РАСПИСАНИЯ' : page === 'patients' || page === 'patient' ? 'КАРТОТЕКА ПАЦИЕНТОВ' : formatDay(scheduleDate).toLocaleUpperCase('ru-RU')
  const stock = stockSection(page)
  const drive = driveView(page)

  return (
    <Box className="app-shell">
      <Box className="desktop-sidebar"><Sidebar className="sidebar" page={page} service={service} staff={staff} live={live} visits={visits} activeVisit={page === 'visit' ? visit : null} onNavigate={navigate} onVisit={openVisit} /></Box>
      <Drawer open={drawer} onClose={() => setDrawer(false)}><Sidebar className="sidebar sidebar-in-drawer" page={page} service={service} staff={staff} live={live} visits={visits} activeVisit={page === 'visit' ? visit : null} onNavigate={navigate} onVisit={(target) => { openVisit(target); setDrawer(false) }} /></Drawer>
      <Box component="main" className="main">
        <Landscape />
        <Topbar eyebrow={eyebrow} title={page === 'today' ? '' : pageTitles[page]} services={servicesFor(role)} service={service} onService={openService} live={live} clinicName={staff.clinicName} onPassword={() => { setPasswordError(''); setPassword(true) }} onSignOut={onSignOut} onMenu={() => setDrawer(true)} />
        <Box className="content">
          {page === 'today' && <TodayPage role={role} selfName={staff.fullName} onNewAppointment={() => openBooking(localToday())} onNewPatient={() => { setCreateError(''); setPatientDialog(true) }} apiOnline={clinic.apiOnline} practitioners={practitioners} patients={patientItems} localAppointments={scheduleItems} localCharges={clinic.localCharges} dueTasks={registry.todayTasks} tasksError={registry.error} staffName={registry.staffName} onPatient={openPatientOf} onOpen={navigate} />}
          {/* Врача встречают на расписании; остальных — на «Сегодня». */}
          {page === 'schedule' && canWriteClinical(role) && <Typography className="greeting" sx={{ mb: 1.5 }}>{greeting(staff.fullName)}</Typography>}
          {page === 'schedule' && canWriteClinical(role) && <DashboardWidgets apiOnline={clinic.apiOnline} />}
          {page === 'schedule' && <Schedule role={role} doctorId={doctorId} date={scheduleDate} view={clinic.scheduleView} appointments={scheduleItems} practitioners={practitioners} doctorFilter={doctorFilter} onDoctorFilter={setDoctorFilter} apiOnline={clinic.apiOnline} onDate={clinic.setScheduleDate} onView={clinic.setScheduleView} onBooking={openBooking} onStatus={(item, to) => clinic.changeStatus(item, to, role)} onPatient={openPatientOf} onStartVisit={startVisit} onReschedule={(item) => { setMoveError(''); setMoving(item) }} onCancel={(item) => clinic.cancelBooking(item, role)} waitlistCount={(item) => registry.matches(item.practitionerId, item.date).length} onWaitlist={(item) => { setWaitError(''); setWaitSlot(item) }} />}
          {page === 'patients' && <PatientsList query={query} setQuery={setQuery} patients={visiblePatients} onPatient={openPatient} onCreate={() => setPatientDialog(true)} />}
          {page === 'patient' && selectedPatient && <PatientCard key={selectedPatient.id} patient={selectedPatient} role={role} doctorId={doctorId} apiOnline={clinic.apiOnline} appointments={scheduleItems} practitioners={practitioners} revision={clinic.revision}
            files={<PatientFiles key={selectedPatient.id} patientId={selectedPatient.id} role={role} apiOnline={clinic.apiOnline} notify={clinic.notify} />}
            registry={canSeeRegistry(role) ? <RegistryPanel patient={selectedPatient} sharedWith={sharedPhone(selectedPatient)} admin={canManageRegistry(role)} selfId={staff.id} registry={registry} onOpenPatient={openPatientById} onSave={(input) => clinic.updateRegistry(selectedPatient, input)} /> : undefined}
            onEdit={() => { setEditError(''); setEditPatient(selectedPatient) }} onBack={() => navigate('patients')} onCancel={(item) => clinic.cancelBooking(item, role)} onReschedule={(item) => { setMoveError(''); setMoving(item) }} onOpenEncounter={(encounter) => openEncounter(selectedPatient, encounter)} onGrantConsent={(input) => clinic.grantConsent(selectedPatient, input)} onRevokeConsent={(consentId) => clinic.revokeConsent(selectedPatient, consentId)} />}
          {page === 'documents' && <SignedDocuments role={role} apiOnline={clinic.apiOnline} practitioners={practitioners} />}
          {page === 'registry' && <RegistryPage staff={staff} registry={registry} patients={patientItems} practitioners={practitioners} services={clinic.services} />}
          {page === 'analytics' && <Analytics apiOnline={clinic.apiOnline} anchor={scheduleDate} practitioners={practitioners} scheduleItems={scheduleItems} />}
          {page === 'audit' && <AuditLog apiOnline={clinic.apiOnline} />}
          {page === 'cashier' && <Cashier role={role} apiOnline={clinic.apiOnline} practitioners={practitioners} localAppointments={scheduleItems} localCharges={clinic.localCharges} onLocalCharge={clinic.addLocalCharge} onLocalPay={clinic.payLocalCharge} onLocalAmount={clinic.setLocalChargeAmount} notify={clinic.notify} />}
          {(page === 'hours' || page === 'prices') && <SettingsPage key={page} section={page} practitioners={practitioners} services={clinic.services} onSaveHours={clinic.saveHours} onSaveCard={clinic.saveCard} onSavePrice={clinic.savePrice} onCreateService={clinic.addService} onToggleService={clinic.toggleService} canEditPrices={canManagePrices(role)} />}
          {page === 'outbox' && <OutboxPage apiOnline={clinic.apiOnline} />}
          {page === 'sales' && <SalesPage apiOnline={clinic.apiOnline} />}
          {stock && <InventoryPage role={role} apiOnline={clinic.apiOnline} services={clinic.services} notify={clinic.notify} section={stock} onSection={(next) => navigate(`stock-${next}`)} />}
          {drive && <DrivePage role={role} apiOnline={clinic.apiOnline} self={staff} notify={clinic.notify} section={drive} onSection={(next) => navigate(next === 'mine' ? 'drive' : `drive-${next}`)} />}
          {page === 'staff' && <StaffPage self={staff} live={live} practitioners={practitioners} notify={clinic.notify} onPractitionerAdded={clinic.practitionerAdded} />}
          {page === 'visit' && visit && <Visit key={visitKey} target={visit} doctorName={doctorName} apiOnline={clinic.apiOnline} notify={clinic.notify} appointmentStatus={visitAppointment?.statusCode} onStarted={() => { if (visitAppointment) void clinic.visitStarted(visitAppointment) }} onComplete={() => { if (visitAppointment) void clinic.changeStatus(visitAppointment, 'COMPLETED', role) }} onBack={() => navigate('schedule')} />}
        </Box>
      </Box>

      <BookingDialog key={bookingKey} checkPast={clinic.apiOnline} open={Boolean(booking)} patients={patientItems} practitioners={practitioners} services={clinic.services} defaultPractitionerId={booking?.practitionerId ?? (doctorFilter === 'all' ? practitioners[0]?.id ?? PETROV_ID : doctorFilter)} error={bookingError} initialDate={booking?.date ?? scheduleDate} onClose={() => { setBooking(null); setBookingError('') }} onSave={async (draft) => {
        const result = await clinic.book(draft)
        if (result.status === 'error') setBookingError(result.message)
        if (result.status === 'done') {
          setBooking(null)
          setBookingError('')
        }
      }} />
      {moving && <RescheduleDialog key={moving.id} item={moving} practitioners={practitioners} error={moveError} onClose={() => setMoving(null)} onSave={async (draft) => {
        const result = await clinic.reschedule(moving, draft)
        if (result.status === 'error') setMoveError(result.message)
        if (result.status === 'done') setMoving(null)
      }} />}
      {waitSlot && <WaitlistMatchesDialog slot={waitSlot} matches={registry.matches(waitSlot.practitionerId, waitSlot.date)} patients={patientItems} services={clinic.services} error={waitError} onClose={() => setWaitSlot(null)} onBook={(entry) => void bookWaiting(waitSlot, entry)} />}
      {patientDialog && <PatientDialog open patients={patientItems} administrators={administrators} error={createError} onClose={() => { setPatientDialog(false); setCreateError('') }} onSave={async (input) => {
        const error = await clinic.createPatient(input, assignedOf(input.assignedStaffId))
        if (error) {
          setCreateError(error)
          return
        }
        setPatientDialog(false)
        setCreateError('')
      }} />}
      {editPatient && <PatientDialog key={editPatient.id} open mode="edit" initial={{ fullName: editPatient.name, birthDate: editPatient.birthDate, phone: editPatient.phone, secondPhone: editPatient.secondPhone ?? '', email: editPatient.email ?? '', assignedStaffId: editPatient.assignedStaff?.id ?? null }} patients={patientItems} selfId={editPatient.id} administrators={administrators} currentAssigned={editPatient.assignedStaff} error={editError} onClose={() => { setEditPatient(null); setEditError('') }} onSave={async (input) => {
        const error = await clinic.updatePatient(editPatient, input, assignedOf(input.assignedStaffId, editPatient.assignedStaff))
        if (error) {
          setEditError(error)
          return
        }
        setEditPatient(null)
        setEditError('')
      }} />}
      {password && <PasswordDialog requireCurrent title="Смена пароля" error={passwordError} onClose={() => setPassword(false)} onSave={async (input) => {
        try {
          await changePassword(input.currentPassword, input.newPassword)
          setPassword(false)
          clinic.notify('Пароль изменён')
        } catch (error) {
          setPasswordError((error as Error).message)
        }
      }} />}
      <Snackbar open={clinic.notice.open} autoHideDuration={2500} onClose={clinic.closeNotice} message={clinic.notice.message} />
    </Box>
  )
}
