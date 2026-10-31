import { PETROV_ID } from '../api/client'
import type { PatientItem, ScheduleItem, StaffItem, WaitlistItem } from '../api/types'
import { AssistantDialog } from '../features/assistant/AssistantDialog'
import type { NavigationDestination } from '../features/assistant/navigationCatalog'
import { PasswordDialog } from '../features/auth/PasswordDialog'
import { PatientDialog } from '../features/patients/PatientDialog'
import { PostgresImportDialog } from '../features/patients/PostgresImportDialog'
import { WaitlistMatchesDialog } from '../features/registry/WaitlistMatchesDialog'
import type { Registry } from '../features/registry/useRegistry'
import { BookingDialog } from '../features/schedule/BookingDialog'
import { RescheduleDialog } from '../features/schedule/RescheduleDialog'
import { SettingsDialog } from '../features/settings/SettingsDialog'
import type { Role } from '../shared/roles'
import type { Page } from './pages'
import type { ClinicData } from './useClinicData'

type PatientInput = Parameters<ClinicData['createPatient']>[0]
type BookingInput = Parameters<ClinicData['book']>[0]
type RescheduleInput = Parameters<ClinicData['reschedule']>[1]
type PendingBookingPatient = { draft: BookingInput; fullName: string; patient?: PatientItem }

const patientInput = (patient: PatientItem): PatientInput => ({ fullName: patient.name, birthDate: patient.birthDate, phone: patient.phone, secondPhone: patient.secondPhone ?? '', email: patient.email ?? '', assignedStaffId: patient.assignedStaff?.id ?? null, profile: patient.profile ?? undefined })

/** Быстрая форма принимает ФИО в порядке «Фамилия Имя Отчество». */
const newPatientInput = (fullName: string): PatientInput => {
  const [lastName = '', firstName = '', ...middle] = fullName.split(/\s+/)
  return { fullName, birthDate: '', phone: '', secondPhone: '', email: '', assignedStaffId: null, profile: { lastName, firstName, middleName: middle.join(' ') } }
}

type WorkspaceDialogsProps = {
  staff: StaffItem
  role: Role
  live: boolean
  page: Page
  clinic: ClinicData
  registry: Registry
  administrators: StaffItem[]
  doctorFilter: string
  booking: { date: string; practitionerId?: string; time?: string } | null
  bookingKey: number
  bookingError: string
  pendingBookingPatient: PendingBookingPatient | null
  moving: ScheduleItem | null
  moveError: string
  waitSlot: ScheduleItem | null
  waitError: string
  patientDialog: boolean
  createError: string
  patientImport: boolean
  editPatient: PatientItem | null
  editError: string
  settings: boolean
  password: boolean
  passwordError: string
  assistant: boolean
  onBookingClose: () => void
  onBookingSave: (draft: BookingInput) => Promise<void>
  onBookingNewPatient: (draft: BookingInput, fullName: string) => void
  onBookingPatientClose: () => void
  onBookingPatientSave: (input: PatientInput) => Promise<void>
  onMovingClose: () => void
  onMovingSave: (draft: RescheduleInput) => Promise<void>
  onWaitlistClose: () => void
  onBookWaiting: (entry: WaitlistItem) => void
  onPatientCreateClose: () => void
  onPatientCreate: (input: PatientInput) => Promise<void>
  onPatientImportClose: () => void
  onPatientEditClose: () => void
  onPatientEdit: (input: PatientInput) => Promise<void>
  onSettingsClose: () => void
  onPasswordOpen: () => void
  onPasswordClose: () => void
  onPasswordSave: (input: { currentPassword: string; newPassword: string }) => Promise<void>
  onAssistantClose: () => void
  onAssistantNavigate: (destination: NavigationDestination) => void
}

/** Модальные действия рабочего места вынесены из компоновки страниц. */
export function WorkspaceDialogs(props: WorkspaceDialogsProps) {
  const {
    staff, role, live, page, clinic, registry, administrators, doctorFilter,
    booking, bookingKey, bookingError, pendingBookingPatient, moving, moveError, waitSlot, waitError,
    patientDialog, createError, patientImport, editPatient, editError, settings,
    password, passwordError, assistant, onBookingClose, onBookingSave, onBookingNewPatient,
    onBookingPatientClose, onBookingPatientSave, onMovingClose,
    onMovingSave, onWaitlistClose, onBookWaiting, onPatientCreateClose, onPatientCreate,
    onPatientImportClose, onPatientEditClose, onPatientEdit, onSettingsClose,
    onPasswordOpen, onPasswordClose, onPasswordSave, onAssistantClose, onAssistantNavigate,
  } = props
  const { patientItems, practitioners, scheduleDate } = clinic

  return (
    <>
      <BookingDialog key={bookingKey} checkPast={clinic.apiOnline} open={Boolean(booking)} patients={patientItems} practitioners={practitioners} services={clinic.services} defaultPractitionerId={booking?.practitionerId ?? (doctorFilter === 'all' ? practitioners[0]?.id ?? PETROV_ID : doctorFilter)} error={bookingError} initialDate={booking?.date ?? scheduleDate} initialTime={booking?.time} onClose={onBookingClose} onSave={onBookingSave} onSaveNewPatient={onBookingNewPatient} />
      {pendingBookingPatient && <PatientDialog key={pendingBookingPatient.patient?.id ?? 'booking-new-patient'} open mode={pendingBookingPatient.patient ? 'edit' : 'create'} initial={pendingBookingPatient.patient ? patientInput(pendingBookingPatient.patient) : newPatientInput(pendingBookingPatient.fullName)} patients={patientItems} selfId={pendingBookingPatient.patient?.id} administrators={administrators} currentAssigned={pendingBookingPatient.patient?.assignedStaff} error={createError} onClose={onBookingPatientClose} onSave={onBookingPatientSave} />}
      {moving && <RescheduleDialog key={moving.id} item={moving} practitioners={practitioners} error={moveError} onClose={onMovingClose} onSave={onMovingSave} />}
      {waitSlot && <WaitlistMatchesDialog slot={waitSlot} matches={registry.matches(waitSlot.practitionerId, waitSlot.date)} patients={patientItems} services={clinic.services} error={waitError} onClose={onWaitlistClose} onBook={onBookWaiting} />}
      {patientDialog && <PatientDialog open patients={patientItems} administrators={administrators} error={createError} onClose={onPatientCreateClose} onSave={onPatientCreate} />}
      {patientImport && <PostgresImportDialog onClose={onPatientImportClose} onDone={(message) => { void clinic.refreshPatients(); clinic.notify(message) }} />}
      {editPatient && <PatientDialog key={editPatient.id} open mode="edit" initial={patientInput(editPatient)} patients={patientItems} selfId={editPatient.id} administrators={administrators} currentAssigned={editPatient.assignedStaff} error={editError} onClose={onPatientEditClose} onSave={onPatientEdit} />}
      <SettingsDialog open={settings} staff={staff} live={live} onClose={onSettingsClose} onPassword={onPasswordOpen} />
      {password && <PasswordDialog requireCurrent title="Смена пароля" error={passwordError} onClose={onPasswordClose} onSave={onPasswordSave} />}
      <AssistantDialog open={assistant} role={role} currentPage={page} apiOnline={clinic.apiOnline} onClose={onAssistantClose} onNavigate={onAssistantNavigate} />
    </>
  )
}
