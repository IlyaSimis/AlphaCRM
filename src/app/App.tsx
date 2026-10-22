import { useEffect, useMemo, useState } from 'react'
import { Box, Drawer, Snackbar } from '@mui/material'
import { PETROV_ID, setDoctorActor } from '../api/client'
import type { EncounterView, PatientItem, ScheduleItem } from '../api/types'
import { Analytics } from '../features/analytics/Analytics'
import { AuditLog } from '../features/audit/AuditLog'
import { Cashier } from '../features/cashier/Cashier'
import { PatientCard } from '../features/patients/PatientCard'
import { PatientDialog } from '../features/patients/PatientDialog'
import { PatientsList } from '../features/patients/PatientsList'
import { filterPatients } from '../features/patients/search'
import { BookingDialog } from '../features/schedule/BookingDialog'
import { RescheduleDialog } from '../features/schedule/RescheduleDialog'
import { Schedule } from '../features/schedule/Schedule'
import { Visit } from '../features/visit/Visit'
import type { VisitTarget } from '../features/visit/visit'
import { formatDay } from '../shared/dates'
import { apiRoles, parseDoctor, parseRole, profileFor, type Role } from '../shared/roles'
import { readStored, writeStored } from '../shared/storage'
import { canOpen, pageTitles, type Page } from './pages'
import { Sidebar } from './Sidebar'
import { Topbar } from './Topbar'
import { useClinicData } from './useClinicData'

const roleKey = 'quiet-clinic-role'
const doctorKey = 'quiet-clinic-doctor'

function App() {
  const [role, setRole] = useState<Role>(() => readStored(roleKey, parseRole))
  const [doctorId, setDoctorId] = useState(() => readStored(doctorKey, parseDoctor))
  // Врач выбирается до первого запроса: этот id уходит в X-Actor-Id при роли DOCTOR.
  setDoctorActor(doctorId)
  const clinic = useClinicData(doctorId)
  const [page, setPage] = useState<Page>('schedule')
  const [query, setQuery] = useState('')
  const [drawer, setDrawer] = useState(false)
  const [doctorFilter, setDoctorFilter] = useState('all')
  const [booking, setBooking] = useState(false)
  const [bookingKey, setBookingKey] = useState(0)
  const [bookingError, setBookingError] = useState('')
  const [moving, setMoving] = useState<ScheduleItem | null>(null)
  const [moveError, setMoveError] = useState('')
  const [patientDialog, setPatientDialog] = useState(false)
  const [editPatient, setEditPatient] = useState<PatientItem | null>(null)
  const [editError, setEditError] = useState('')
  const [visit, setVisit] = useState<VisitTarget | null>(null)
  const [visitKey, setVisitKey] = useState(0)

  useEffect(() => { writeStored(roleKey, role) }, [role])
  useEffect(() => { writeStored(doctorKey, doctorId) }, [doctorId])

  const { patientItems, scheduleItems, practitioners, scheduleDate } = clinic
  const visiblePatients = useMemo(() => filterPatients(patientItems, query), [patientItems, query])
  const selectedPatient = patientItems.find((patient) => patient.id === clinic.selectedPatientId) ?? patientItems[0]
  const doctorName = profileFor('Врач', doctorId).name

  const navigate = (next: Page) => {
    setPage(canOpen(next, role) ? next : 'schedule')
    setDrawer(false)
  }

  const openBooking = () => {
    setBookingError('')
    setBookingKey((value) => value + 1)
    setBooking(true)
  }

  const openPatient = (patient: PatientItem) => {
    clinic.setSelectedPatientId(patient.id)
    navigate('patient')
  }

  const openPatientOf = (item: ScheduleItem) => {
    const match = patientItems.find((patient) => (item.patientId ? patient.id === item.patientId : patient.name === item.name)) ?? patientItems.find((patient) => patient.name === item.name)
    if (match) openPatient(match)
  }

  const openVisit = (target: VisitTarget) => {
    setVisit(target)
    setVisitKey((value) => value + 1)
    navigate('visit')
  }

  const startVisit = (item: ScheduleItem) => openVisit({
    patientId: item.patientId,
    patientName: item.name,
    practitionerId: item.practitionerId,
    appointmentId: item.id,
    date: item.date,
    time: item.time,
    minutes: item.minutes,
    serviceName: item.type,
  })

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

  const changeRole = (next: Role) => {
    setRole(next)
    setVisit(null)
    setPage('schedule')
    setDrawer(false)
  }

  const changeDoctor = (next: string) => {
    setDoctorId(next)
    setVisit(null)
    if (page === 'visit') setPage('schedule')
  }

  const eyebrow = page === 'visit'
    ? 'КЛИНИЧЕСКИЙ КОНТУР · ДОСТУП ПО СВЯЗИ С ЛЕЧЕНИЕМ'
    : page === 'analytics' || page === 'audit' || page === 'cashier'
      ? 'УПРАВЛЕНИЕ КЛИНИКОЙ'
      : formatDay(scheduleDate).toLocaleUpperCase('ru-RU')

  return (
    <Box className="app-shell">
      <Box className="desktop-sidebar"><Sidebar className="sidebar" page={page} role={role} doctorId={doctorId} visitOpen={Boolean(visit)} onNavigate={navigate} /></Box>
      <Drawer open={drawer} onClose={() => setDrawer(false)}><Sidebar className="sidebar sidebar-in-drawer" page={page} role={role} doctorId={doctorId} visitOpen={Boolean(visit)} onNavigate={navigate} /></Drawer>
      <Box component="main" className="main">
        <Topbar eyebrow={eyebrow} title={pageTitles[page]} role={role} doctorId={doctorId} onRole={changeRole} onDoctor={changeDoctor} onMenu={() => setDrawer(true)} />
        <Box className="content">
          {page === 'schedule' && <Schedule role={role} doctorId={doctorId} date={scheduleDate} appointments={scheduleItems} practitioners={practitioners} doctorFilter={doctorFilter} onDoctorFilter={setDoctorFilter} apiOnline={clinic.apiOnline} onDate={clinic.setScheduleDate} onBooking={openBooking} onStatus={(item, to) => clinic.changeStatus(item, to, role)} onPatient={openPatientOf} onStartVisit={startVisit} onReschedule={(item) => { setMoveError(''); setMoving(item) }} onCancel={(item) => clinic.cancelBooking(item, role)} />}
          {page === 'patients' && <PatientsList query={query} setQuery={setQuery} patients={visiblePatients} onPatient={openPatient} onCreate={() => setPatientDialog(true)} />}
          {page === 'patient' && selectedPatient && <PatientCard key={selectedPatient.id} patient={selectedPatient} role={role} doctorId={doctorId} apiOnline={clinic.apiOnline} appointments={scheduleItems} practitioners={practitioners} revision={clinic.revision} onEdit={() => { setEditError(''); setEditPatient(selectedPatient) }} onBack={() => navigate('patients')} onCancel={(item) => clinic.cancelBooking(item, role)} onReschedule={(item) => { setMoveError(''); setMoving(item) }} onOpenEncounter={(encounter) => openEncounter(selectedPatient, encounter)} onGrantConsent={(input) => clinic.grantConsent(selectedPatient, input)} onRevokeConsent={(consentId) => clinic.revokeConsent(selectedPatient, consentId)} />}
          {page === 'analytics' && <Analytics apiOnline={clinic.apiOnline} role={apiRoles[role]} anchor={scheduleDate} practitioners={practitioners} scheduleItems={scheduleItems} />}
          {page === 'audit' && <AuditLog apiOnline={clinic.apiOnline} role={apiRoles[role]} />}
          {page === 'cashier' && <Cashier role={role} apiRole={apiRoles[role]} apiOnline={clinic.apiOnline} practitioners={practitioners} localAppointments={scheduleItems} localCharges={clinic.localCharges} onLocalCharge={clinic.addLocalCharge} onLocalPay={clinic.payLocalCharge} notify={clinic.notify} />}
          {page === 'visit' && visit && <Visit key={visitKey} target={visit} doctorName={doctorName} apiOnline={clinic.apiOnline} notify={clinic.notify} onBack={() => navigate('schedule')} />}
        </Box>
      </Box>

      <BookingDialog key={bookingKey} open={booking} patients={patientItems} practitioners={practitioners} services={clinic.services} defaultPractitionerId={doctorFilter === 'all' ? practitioners[0]?.id ?? PETROV_ID : doctorFilter} error={bookingError} initialDate={scheduleDate} onClose={() => { setBooking(false); setBookingError('') }} onSave={async (draft) => {
        const result = await clinic.book(draft, role)
        if (result.status === 'error') setBookingError(result.message)
        if (result.status === 'done') {
          setBooking(false)
          setBookingError('')
        }
      }} />
      {moving && <RescheduleDialog key={moving.id} item={moving} practitioners={practitioners} error={moveError} onClose={() => setMoving(null)} onSave={async (draft) => {
        const result = await clinic.reschedule(moving, draft)
        if (result.status === 'error') setMoveError(result.message)
        if (result.status === 'done') setMoving(null)
      }} />}
      <PatientDialog open={patientDialog} onClose={() => setPatientDialog(false)} onSave={async (input) => {
        await clinic.createPatient(input)
        setPatientDialog(false)
      }} />
      {editPatient && <PatientDialog key={editPatient.id} open mode="edit" initial={{ fullName: editPatient.name, birthDate: editPatient.birthDate, phone: editPatient.phone }} error={editError} onClose={() => { setEditPatient(null); setEditError('') }} onSave={async (input) => {
        const error = await clinic.updatePatient(editPatient, input)
        if (error) {
          setEditError(error)
          return
        }
        setEditPatient(null)
        setEditError('')
      }} />}
      <Snackbar open={clinic.notice.open} autoHideDuration={2500} onClose={clinic.closeNotice} message={clinic.notice.message} />
    </Box>
  )
}

export default App
