import { Suspense } from 'react'
import { Box, CircularProgress, Typography } from '@mui/material'
import type { EncounterView, PatientItem, ScheduleItem, StaffItem } from '../api/types'
import type { WorkspaceNavigationIntent } from '../features/assistant/navigationCatalog'
import { DashboardWidgets } from '../features/today/DashboardWidgets'
import { dashboardCodes } from '../features/today/dashboardCodes'
import { TodayPage } from '../features/today/TodayPage'
import { Schedule } from '../features/schedule/Schedule'
import type { Registry } from '../features/registry/useRegistry'
import type { VisitTarget } from '../features/visit/visitRules'
import { localToday } from '../shared/dates'
import { greeting } from '../shared/greeting'
import { canEditPatients, canManagePrices, canManageRegistry, canSeePatients, canSeeRegistry, canWriteClinical, type Role } from '../shared/roles'
import type { ClinicData } from './useClinicData'
import { driveView, stockSection, type Page } from './pages'
import {
  Analytics, AuditLog, BreakGlassLog, Cashier, ContractsPage, ContractTemplatesPage, DrivePage, InventoryPage, MyContractPage,
  OutboxPage, PatientCard, PatientContracts, PatientFiles, PatientsList, RegistryPage, RegistryPanel, SalesPage,
  SettingsPage, SignedDocuments, StaffPage, Visit, WorkspacePage, IntegrationsPage,
} from './lazyPages'

type WorkspaceContentProps = {
  page: Page
  staff: StaffItem
  role: Role
  doctorId: string
  live: boolean
  clinic: ClinicData
  registry: Registry
  query: string
  visiblePatients: PatientItem[]
  selectedPatient?: PatientItem
  doctorFilter: string
  workspaceIntent: ({ key: number } & WorkspaceNavigationIntent) | null
  visit: VisitTarget | null
  visitKey: number
  visitAppointment?: ScheduleItem
  doctorName: string
  sharedPhone: (patient: PatientItem) => Array<{ id: string; fullName: string }>
  onQuery: (query: string) => void
  onDoctorFilter: (id: string) => void
  onNavigate: (page: Page) => void
  onOpenBooking: (date?: string, practitionerId?: string, time?: string, patientId?: string) => void
  onNewPatient: () => void
  onCreatePatient: () => void
  onImportPatients: () => void
  onEditPatient: (patient: PatientItem, addRepresentative?: boolean) => void
  onOpenPatient: (patient: PatientItem) => void
  onOpenPatientById: (id: string) => void
  onOpenPatientOf: (item: ScheduleItem) => void
  onOpenEncounter: (patient: PatientItem, encounter: EncounterView) => void
  onStartVisit: (item: ScheduleItem) => void
  onReschedule: (item: ScheduleItem) => void
  onWaitlist: (item: ScheduleItem) => void
}

/** Текущий раздел рабочего места. Нестартовые страницы подключаются через React.lazy. */
export function WorkspaceContent(props: WorkspaceContentProps) {
  const {
    page, staff, role, doctorId, live, clinic, registry, query, visiblePatients, selectedPatient,
    doctorFilter, workspaceIntent, visit, visitKey, visitAppointment, doctorName, sharedPhone,
    onQuery, onDoctorFilter, onNavigate, onOpenBooking, onNewPatient, onCreatePatient,
    onImportPatients, onEditPatient, onOpenPatient, onOpenPatientById, onOpenPatientOf,
    onOpenEncounter, onStartVisit, onReschedule, onWaitlist,
  } = props
  const { patientItems, scheduleItems, practitioners, scheduleDate } = clinic
  const stock = stockSection(page)
  const drive = driveView(page)

  return (
    <Suspense fallback={<Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress aria-label="Загрузка раздела" /></Box>}>
      {page === 'today' && <TodayPage role={role} selfId={staff.id} selfName={staff.fullName} onNewAppointment={() => onOpenBooking(localToday())} onNewPatient={onNewPatient} apiOnline={clinic.apiOnline} patients={patientItems} appointments={scheduleItems} practitioners={practitioners} revision={clinic.revision} dueTasks={registry.todayTasks} tasksError={registry.error} staffName={registry.staffName} onOpen={onNavigate} onOpenPatient={onOpenPatientById} onRebook={(item) => onOpenBooking(localToday(), item.practitionerId, undefined, item.patientId)} />}
      {page === 'workspace' && <WorkspacePage key={staff.id} apiOnline={clinic.apiOnline} selfId={staff.id} staff={registry.staffList} patients={canSeePatients(role) ? patientItems : []} appointments={scheduleItems} notify={clinic.notify} navigationIntent={workspaceIntent} />}
      {/* Врача встречают на расписании; остальных — на «Сегодня». */}
      {page === 'schedule' && canWriteClinical(role) && <Typography className="greeting" sx={{ mb: 1.5 }}>{greeting(staff.fullName)}</Typography>}
      {page === 'schedule' && canWriteClinical(role) && <DashboardWidgets apiOnline={clinic.apiOnline} role={role} codes={dashboardCodes(role)} onOpen={onNavigate} />}
      {page === 'schedule' && <Schedule role={role} doctorId={doctorId} date={scheduleDate} view={clinic.scheduleView} appointments={scheduleItems} practitioners={practitioners} doctorFilter={doctorFilter} onDoctorFilter={onDoctorFilter} apiOnline={clinic.apiOnline} onDate={clinic.setScheduleDate} onView={clinic.setScheduleView} onBooking={onOpenBooking} onStatus={(item, to) => clinic.changeStatus(item, to, role)} onPatient={onOpenPatientOf} onStartVisit={onStartVisit} onReschedule={onReschedule} onCancel={(item) => clinic.cancelBooking(item, role)} waitlistCount={(item) => registry.matches(item.practitionerId, item.date).length} onWaitlist={onWaitlist} />}
      {page === 'patients' && <PatientsList query={query} setQuery={onQuery} patients={visiblePatients} onPatient={onOpenPatient} onCreate={onCreatePatient} onImport={clinic.apiOnline && canEditPatients(role) ? onImportPatients : undefined} />}
      {page === 'patient' && selectedPatient && <PatientCard key={selectedPatient.id} patient={selectedPatient} role={role} doctorId={doctorId} apiOnline={clinic.apiOnline} appointments={scheduleItems} practitioners={practitioners} revision={clinic.revision}
        files={<Box>
          <PatientContracts patientId={selectedPatient.id} role={role} apiOnline={clinic.apiOnline} services={clinic.services} notify={clinic.notify} />
          <PatientFiles patientId={selectedPatient.id} role={role} apiOnline={clinic.apiOnline} notify={clinic.notify} />
        </Box>}
        registry={canSeeRegistry(role) ? <RegistryPanel patient={selectedPatient} sharedWith={sharedPhone(selectedPatient)} admin={canManageRegistry(role)} selfId={staff.id} registry={registry} onOpenPatient={onOpenPatientById} onSave={(input) => clinic.updateRegistry(selectedPatient, input)} /> : undefined}
        onEdit={() => onEditPatient(selectedPatient)} onAddRepresentative={() => onEditPatient(selectedPatient, true)} onBack={() => onNavigate('patients')} onCancel={(item) => clinic.cancelBooking(item, role)} onReschedule={onReschedule} onOpenEncounter={(encounter) => onOpenEncounter(selectedPatient, encounter)} onGrantConsent={(input) => clinic.grantConsent(selectedPatient, input)} onRevokeConsent={(consentId) => clinic.revokeConsent(selectedPatient, consentId)} />}
      {page === 'documents' && <SignedDocuments role={role} apiOnline={clinic.apiOnline} practitioners={practitioners} />}
      {page === 'contract-templates' && <ContractTemplatesPage role={role} apiOnline={clinic.apiOnline} services={clinic.services} notify={clinic.notify} />}
      {page === 'registry' && <RegistryPage staff={staff} registry={registry} patients={patientItems} practitioners={practitioners} services={clinic.services} />}
      {page === 'analytics' && <Analytics apiOnline={clinic.apiOnline} anchor={scheduleDate} practitioners={practitioners} scheduleItems={scheduleItems} onOpenPatient={onOpenPatientById} />}
      {page === 'audit' && <AuditLog apiOnline={clinic.apiOnline} />}
      {page === 'cashier' && <Cashier role={role} apiOnline={clinic.apiOnline} practitioners={practitioners} localAppointments={scheduleItems} localCharges={clinic.localCharges} onLocalCharge={clinic.addLocalCharge} onLocalPay={clinic.payLocalCharge} onLocalAmount={clinic.setLocalChargeAmount} notify={clinic.notify} />}
      {(page === 'hours' || page === 'prices') && <SettingsPage key={page} section={page} practitioners={practitioners} services={clinic.services} onSaveHours={clinic.saveHours} onSaveCard={clinic.saveCard} onSavePrice={clinic.savePrice} onCreateService={clinic.addService} canEditPrices={canManagePrices(role)} />}
      {page === 'outbox' && <OutboxPage apiOnline={clinic.apiOnline} />}
      {page === 'integrations' && <IntegrationsPage staff={staff} apiOnline={clinic.apiOnline} notify={clinic.notify} />}
      {page === 'sales' && <SalesPage apiOnline={clinic.apiOnline} />}
      {page === 'break-glass' && <BreakGlassLog apiOnline={clinic.apiOnline} />}
      {page === 'hr' && <ContractsPage apiOnline={clinic.apiOnline} notify={clinic.notify} />}
      {page === 'hr-me' && <MyContractPage apiOnline={clinic.apiOnline} />}
      {stock && <InventoryPage role={role} apiOnline={clinic.apiOnline} services={clinic.services} notify={clinic.notify} section={stock} onSection={(next) => onNavigate(`stock-${next}`)} />}
      {drive && <DrivePage apiOnline={clinic.apiOnline} self={staff} notify={clinic.notify} section={drive} onSection={(next) => onNavigate(next === 'mine' ? 'drive' : `drive-${next}`)} />}
      {page === 'staff' && <StaffPage self={staff} live={live} practitioners={practitioners} notify={clinic.notify} onPractitionerAdded={clinic.practitionerAdded} />}
      {page === 'visit' && visit && <Visit key={visitKey} target={visit} doctorName={doctorName} apiOnline={clinic.apiOnline} notify={clinic.notify} appointmentStatus={visitAppointment?.statusCode} onStarted={() => { if (visitAppointment) void clinic.visitStarted(visitAppointment) }} onComplete={() => { if (visitAppointment) void clinic.changeStatus(visitAppointment, 'COMPLETED', role) }} onBack={() => onNavigate('schedule')} />}
    </Suspense>
  )
}
