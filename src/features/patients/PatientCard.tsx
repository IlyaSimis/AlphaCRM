import { useEffect, useState, type ReactNode } from 'react'
import { CheckCircleRounded, EditRounded, EventNoteRounded, HealthAndSafetyRounded, LockOutlined } from '@mui/icons-material'
import { Alert, Avatar, Box, Button, Chip, CircularProgress, Divider, Typography } from '@mui/material'
import { loadBreakGlass, loadEncounters } from '../../api/clinical'
import { CANCELLABLE } from '../../api/schedule'
import type { BreakGlassGrant, EncounterView, PatientItem, Practitioner, ScheduleItem } from '../../api/types'
import { clinicTimeZone } from '../../shared/clinicTime'
import { localToday } from '../../shared/dates'
import { can } from '../../shared/permissions'
import { canEditPatients, canWriteAppointments, canWriteClinical, roleText, type Role } from '../../shared/roles'
import { initialsOf, isUuid } from '../../shared/text'
import { Info } from '../../shared/ui'
import { BreakGlassDialog } from '../breakglass/BreakGlassDialog'
import { untilText } from '../breakglass/breakGlass'
import { RESCHEDULABLE } from '../schedule/statusActions'
import { ConsentDialog } from './ConsentDialog'
import { consentBases, consentLabels } from './consentLabels'
import { splitHistory } from './history'
import { useClinicalHistory } from './useClinicalHistory'
import { usePatientHistory } from './usePatientHistory'
import { PatientCalls } from '../telephony/PatientCalls'
import { PatientProfileDetails } from './PatientProfileDetails'

type PatientCardProps = {
  patient: PatientItem
  role: Role
  doctorId: string
  apiOnline: boolean
  appointments: ScheduleItem[]
  practitioners: Practitioner[]
  revision: number
  /** Регистратура: источник, заметка, задачи, предупреждение о телефоне. Врачу не показывается. */
  registry?: ReactNode
  /** Блок «Документы PDF»; кассиру не передаётся. */
  files?: ReactNode
  onEdit: () => void
  onAddRepresentative: () => void
  onBack: () => void
  onCancel: (item: ScheduleItem) => void
  onReschedule: (item: ScheduleItem) => void
  onOpenEncounter: (encounter: EncounterView) => void
  onGrantConsent: (input: { version: string; basis: string }) => Promise<string | null>
  onRevokeConsent: (consentId: string) => void
}

function formatDateTime(iso?: string | null) {
  if (!iso) return '—'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: clinicTimeZone() })
}

function shortDay(iso: string) {
  const [year, month, day] = iso.split('-')
  return `${day}.${month}.${year}`
}

export function PatientCard(props: PatientCardProps) {
  const { patient, role, apiOnline, practitioners, revision, onEdit, onBack } = props
  const [consentDialog, setConsentDialog] = useState(false)
  const [consentError, setConsentError] = useState('')
  const [showAllPast, setShowAllPast] = useState(false)
  const history = usePatientHistory(patient, apiOnline, props.appointments, revision)
  const { upcoming, past } = splitHistory(history.items, localToday())
  const visiblePast = showAllPast ? past : past.slice(0, 3)
  const doctorName = (id: string) => practitioners.find((doctor) => doctor.id === id)?.fullName ?? 'Врач'
  const basisLabel = (value: string) => consentBases.find((item) => item.value === value)?.label ?? value

  const row = (item: ScheduleItem) => (
    <Box className="next-appointment" key={item.id ?? `${item.date}-${item.time}`}>
      <EventNoteRounded />
      <Box sx={{ flex: 1 }}><Typography sx={{ fontWeight: 700 }}>{shortDay(item.date)} {item.time} · {item.status}</Typography><Typography sx={{ fontSize: 13 }}>{doctorName(item.practitionerId)} · {item.type.toLowerCase()}</Typography></Box>
      {canWriteAppointments(role) && RESCHEDULABLE.includes(item.statusCode) && <Button size="small" aria-label={`Перенести запись ${item.time} ${item.name}`} onClick={() => props.onReschedule(item)}>Перенести</Button>}
      {canWriteAppointments(role) && CANCELLABLE.includes(item.statusCode) && <Button size="small" variant="outlined" aria-label={`Отменить запись ${item.time} ${item.name}`} onClick={() => props.onCancel(item)}>Отменить</Button>}
    </Box>
  )

  return (
    <>
      <Button onClick={onBack} sx={{ mb: 2 }}>← К результатам поиска</Button>
      <Box className="patient-header">
        <Avatar sx={{ width: 64, height: 64, bgcolor: 'action.selected', color: 'primary.main', fontSize: 22 }}>{initialsOf(patient.name)}</Avatar>
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>{patient.name}</Typography><Typography sx={{ color: 'text.secondary' }}>{patient.birth}{patient.id.startsWith('P-') ? ` · ${patient.id}` : ''}</Typography></Box>
        <Chip icon={<CheckCircleRounded />} label={patient.consent === 'ACTIVE' ? 'Согласие действует' : consentLabels[patient.consent]} color={patient.consent === 'ACTIVE' ? 'success' : 'warning'} variant="outlined" />
        {canEditPatients(role) && <Button variant="outlined" startIcon={<EditRounded />} onClick={onEdit}>Редактировать</Button>}
      </Box>
      <Box className="patient-columns">
        <Box className="panel">
          <Typography className="panel-label">АДМИНИСТРАТИВНЫЕ ДАННЫЕ</Typography>
          <Info label="Телефон" value={patient.phone} />
          {patient.secondPhone && <Info label="Второй телефон" value={patient.secondPhone} />}
          {patient.email && <Info label="E-mail" value={patient.email} />}
          <Info label="Постоянный администратор" value={patient.assignedStaff ? `${patient.assignedStaff.fullName}${patient.assignedStaff.active ? '' : ' · учётная запись отключена'}` : 'не назначен'} />
          <Info label="Каналы уведомлений" value={[patient.profile?.emailNotifications && 'E-mail', patient.profile?.smsNotifications && 'SMS', patient.profile?.messengerNotifications && 'Мессенджеры', patient.profile?.pushNotifications && 'Push'].filter(Boolean).join(' · ') || 'Не выбраны'} />
          {patient.phone !== '—' && <PatientCalls patientId={patient.id} apiOnline={apiOnline} />}
          {props.registry}
          <Divider sx={{ mt: 2 }} />
          <Typography sx={{ fontWeight: 700, mt: 2 }}>Ближайшие записи</Typography>
          {history.loading && <CircularProgress size={22} sx={{ mt: 1 }} aria-label="Загрузка записей" />}
          {history.error && <Alert severity="warning" sx={{ mt: 1 }}>{history.error}</Alert>}
          {!history.loading && (upcoming.length ? upcoming.map(row) : <Typography sx={{ color: 'text.secondary', mt: 1 }}>Ближайших записей нет</Typography>)}
          <Typography sx={{ fontWeight: 700, mt: 2.5 }}>Прошедшие записи</Typography>
          {!history.loading && (past.length ? visiblePast.map(row) : <Typography sx={{ color: 'text.secondary', mt: 1 }}>Прошедших записей нет</Typography>)}
          {!history.loading && past.length > 3 && <Button size="small" sx={{ mt: 1 }} onClick={() => setShowAllPast((value) => !value)}>{showAllPast ? 'Скрыть старые записи' : `Показать ещё (${past.length - 3})`}</Button>}
          {canEditPatients(role) && <>
            <Divider sx={{ my: 2 }} />
            <Box className="panel-title-row"><Typography sx={{ fontWeight: 700 }}>Согласие на обработку данных</Typography><Button size="small" onClick={() => { setConsentError(''); setConsentDialog(true) }}>Обновить согласие</Button></Box>
            {patient.consents.length === 0 && <Typography sx={{ color: 'text.secondary', mt: 1 }}>Согласий нет</Typography>}
            {patient.consents.map((consent) => (
              <Box className="history-item" key={consent.id}>
                <Box><Typography sx={{ fontWeight: 650 }}>Версия {consent.version} · {basisLabel(consent.basis)}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{consent.revokedAt ? `отозвано ${formatDateTime(consent.revokedAt)}` : `действует с ${formatDateTime(consent.grantedAt)}`}</Typography></Box>
                {!consent.revokedAt && <Button size="small" color="warning" aria-label={`Отозвать согласие ${consent.version}`} onClick={() => props.onRevokeConsent(consent.id)}>Отозвать</Button>}
              </Box>
            ))}
          </>}
        </Box>
        <ClinicalPanel {...props} />
      </Box>
      <PatientProfileDetails patient={patient} onAddRepresentative={canEditPatients(role) ? props.onAddRepresentative : undefined} />
      {props.files}
      {consentDialog && <ConsentDialog error={consentError} onClose={() => setConsentDialog(false)} onSave={async (input) => {
        const error = await props.onGrantConsent(input)
        if (error) setConsentError(error)
        else setConsentDialog(false)
      }} />}
    </>
  )
}

function documentLine(state: string, amends?: string | null, signedAt?: string | null) {
  const kind = amends ? 'Дополнение' : 'Документ приёма'
  return state === 'SIGNED_LOCKED' ? `${kind} · подписан ${formatDateTime(signedAt)}` : `${kind} · черновик`
}

function ClinicalPanel({ patient, role, doctorId, apiOnline, revision, onOpenEncounter }: PatientCardProps) {
  const clinicalRole = canWriteClinical(role)
  const enabled = clinicalRole && apiOnline && isUuid(patient.id)
  const clinical = useClinicalHistory(patient.id, enabled, doctorId, revision)
  // Владелец: экстренный доступ к истории этого пациента (K1) вместо «недоступно».
  if (!clinicalRole && can(role, 'clinical.record.break-glass') && apiOnline && isUuid(patient.id)) return <EmergencyPanel patient={patient} />
  return (
    <Box className="panel clinical-panel">
      <Box className="panel-title-row"><Typography className="panel-label">КЛИНИЧЕСКИЙ КОНТУР</Typography><LockOutlined fontSize="small" /></Box>
      {!clinicalRole && <Box className="locked-state"><LockOutlined /><Typography sx={{ fontWeight: 750 }}>Клинические данные недоступны</Typography><Typography sx={{ fontSize: 14 }}>Роль «{roleText(role)}» не имеет доступа к жалобам, диагнозам, назначениям и медицинским документам.</Typography></Box>}
      {clinicalRole && !enabled && <Box className="locked-state"><EventNoteRounded /><Typography sx={{ fontWeight: 750 }}>История приёмов хранится на сервере</Typography><Typography sx={{ fontSize: 14 }}>{apiOnline ? 'У этого пациента нет карты на сервере.' : 'Демо-режим: сервер недоступен, клиническая история не загружается.'}</Typography></Box>}
      {enabled && clinical.loading && <CircularProgress size={24} aria-label="Загрузка истории приёмов" />}
      {enabled && clinical.error && <Alert severity="warning">{clinical.error}</Alert>}
      {enabled && !clinical.loading && !clinical.error && (clinical.encounters.length === 0
        ? <Box className="locked-state"><EventNoteRounded /><Typography sx={{ fontWeight: 750 }}>Приёмов пока нет</Typography><Typography sx={{ fontSize: 14 }}>Клинические записи появятся после приёма, в котором вы участвуете.</Typography></Box>
        : <>
          <Typography sx={{ fontWeight: 700 }}>История приёмов</Typography>
          {clinical.encounters.map((encounter) => (
            <Box className="history-item" key={encounter.encounterId}>
              <Box>
                <Typography sx={{ fontWeight: 650 }}>Приём {formatDateTime(encounter.startedAt)}</Typography>
                {encounter.documents.length === 0 && <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Документа ещё нет</Typography>}
                {encounter.documents.map((doc) => <Typography key={doc.id} sx={{ fontSize: 13, color: 'text.secondary' }}>{doc.state === 'SIGNED_LOCKED' && <LockOutlined sx={{ fontSize: 13, verticalAlign: 'middle', mr: 0.5 }} />}{documentLine(doc.state, doc.amendsDocumentId, doc.signedAt)}</Typography>)}
              </Box>
              <Button size="small" onClick={() => onOpenEncounter(encounter)}>Открыть</Button>
            </Box>
          ))}
        </>)}
    </Box>
  )
}

/** Экстренный доступ владельца: без доступа — кнопка; с доступом — история только для чтения до конца срока. */
function EmergencyPanel({ patient }: { patient: PatientItem }) {
  const [grant, setGrant] = useState<BreakGlassGrant | null>(null)
  const [encounters, setEncounters] = useState<EncounterView[] | null>(null)
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState(false)
  const active = Boolean(grant?.active)

  useEffect(() => {
    let cancelled = false
    loadBreakGlass(patient.id)
      .then((result) => { if (!cancelled) setGrant(result) })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [patient.id])

  useEffect(() => {
    if (!active) return
    let cancelled = false
    loadEncounters(patient.id)
      .then((rows) => { if (!cancelled) setEncounters(rows) })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    // Срок вышел — история закрывается без перезагрузки страницы.
    const left = grant?.expiresAt ? new Date(grant.expiresAt).getTime() - Date.now() : 0
    const timer = window.setTimeout(() => {
      setEncounters(null)
      setGrant((current) => (current ? { ...current, active: false } : current))
    }, Math.max(0, Math.min(left, 2 ** 31 - 1)))
    return () => { cancelled = true; window.clearTimeout(timer) }
  }, [active, patient.id, grant?.expiresAt])

  return (
    <Box className="panel clinical-panel">
      <Box className="panel-title-row"><Typography className="panel-label">КЛИНИЧЕСКИЙ КОНТУР</Typography><LockOutlined fontSize="small" /></Box>
      {error && <Alert severity="warning">{error}</Alert>}
      {!active && <Box className="locked-state">
        <HealthAndSafetyRounded />
        <Typography sx={{ fontWeight: 750 }}>Медицинские данные закрыты</Typography>
        <Typography sx={{ fontSize: 14 }}>Их читает лечащий врач. Владелец клиники может открыть историю этого пациента в экстренном случае — с причиной и паролем, на 60 минут, только чтение.</Typography>
        <Button variant="outlined" color="error" sx={{ mt: 1 }} onClick={() => setDialog(true)}>Экстренный доступ</Button>
      </Box>}
      {active && <>
        <Alert severity="error" icon={<HealthAndSafetyRounded />}>Экстренный доступ {untilText(grant?.expiresAt, clinicTimeZone())}: только чтение, каждое открытие в журнале.</Alert>
        {encounters === null ? <CircularProgress size={24} aria-label="Загрузка истории приёмов" />
          : encounters.length === 0 ? <Typography sx={{ color: 'text.secondary' }}>Приёмов нет</Typography>
          : encounters.map((encounter) => (
            <Box className="history-item emergency-item" key={encounter.encounterId}>
              <Box>
                <Typography sx={{ fontWeight: 650 }}>Приём {formatDateTime(encounter.startedAt)}</Typography>
                {encounter.complaints && <Typography sx={{ fontSize: 13 }}><b>Жалобы:</b> {encounter.complaints}</Typography>}
                {encounter.mentalStatus && <Typography sx={{ fontSize: 13 }}><b>Состояние:</b> {encounter.mentalStatus}</Typography>}
                {encounter.diagnosis && <Typography sx={{ fontSize: 13 }}><b>Диагноз:</b> {encounter.diagnosis}</Typography>}
                {encounter.recommendations && <Typography sx={{ fontSize: 13 }}><b>Рекомендации:</b> {encounter.recommendations}</Typography>}
                {encounter.documents.map((doc) => <Typography key={doc.id} sx={{ fontSize: 13, color: 'text.secondary' }}>{documentLine(doc.state, doc.amendsDocumentId, doc.signedAt)}</Typography>)}
              </Box>
            </Box>
          ))}
      </>}
      {dialog && <BreakGlassDialog patientId={patient.id} patientName={patient.name} onClose={() => setDialog(false)} onGranted={(next) => { setDialog(false); setError(''); setGrant(next) }} />}
    </Box>
  )
}
