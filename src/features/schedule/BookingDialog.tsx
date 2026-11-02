import { useState } from 'react'
import { CloseRounded, PersonAddRounded } from '@mui/icons-material'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, Stack, TextField } from '@mui/material'
import type { PatientItem, Practitioner, ServiceItem } from '../../api/types'
import { bookableServices, priceText, validateBooking, type BookingDraft } from './booking'
import { bookingTimeOptions, OUTSIDE_WORKING_HOURS } from './workingHours'

const NEW_PATIENT = '__new_patient__'

type BookingDialogProps = {
  open: boolean
  patients: PatientItem[]
  practitioners: Practitioner[]
  services: ServiceItem[]
  defaultPractitionerId: string
  error: string
  initialDate: string
  initialTime?: string
  initialPatientId?: string
  onClose: () => void
  onSave: (draft: BookingDraft) => void
  onSaveNewPatient: (draft: BookingDraft, fullName: string) => void
  /** Работаем с сервером: прошедшее время не принимается. */
  checkPast?: boolean
}

export function BookingDialog({ open, patients, practitioners, services: catalog, defaultPractitionerId, error, initialDate, initialTime = '13:00', initialPatientId, onClose, onSave, onSaveNewPatient, checkPast = false }: BookingDialogProps) {
  const services = bookableServices(catalog)
  const fallbackPatientId = patients[0]?.id ?? ''
  const fallbackServiceId = services[0]?.id ?? ''
  const [patientId, setPatientId] = useState(initialPatientId ?? fallbackPatientId)
  const [newPatientName, setNewPatientName] = useState('')
  const [date, setDate] = useState(initialDate)
  const [time, setTime] = useState(initialTime)
  const [practitionerId, setPractitionerId] = useState(defaultPractitionerId)
  const [serviceId, setServiceId] = useState(fallbackServiceId)
  const [localError, setLocalError] = useState('')
  // Диалог получает новый ключ при каждом открытии; сервер повторно проверит время при сохранении.
  const [openedAt] = useState(() => new Date())
  const isNewPatient = patientId === NEW_PATIENT
  const selectedPatientId = isNewPatient || patients.some((item) => item.id === patientId) ? patientId : fallbackPatientId
  const selectedPractitionerId = practitioners.some((item) => item.id === practitionerId) ? practitionerId : practitioners[0]?.id ?? ''
  const selectedServiceId = services.some((item) => item.id === serviceId) ? serviceId : fallbackServiceId
  const [estimate, setEstimate] = useState('')
  const draft: BookingDraft = { patientId: isNewPatient ? '' : selectedPatientId, date, time, serviceId: selectedServiceId, practitionerId: selectedPractitionerId, estimate }
  // У нового пациента пока достаточно ФИО; остальные поля записи проверяем сразу.
  const checkedDraft = { ...draft, patientId: isNewPatient ? fallbackPatientId : draft.patientId }
  const problem = validateBooking(checkedDraft, patients, services, practitioners)
  const service = services.find((item) => item.id === selectedServiceId)
  const practitioner = practitioners.find((item) => item.id === selectedPractitionerId)
  const timeOptions = bookingTimeOptions(practitioner, date, service?.durationMinutes ?? 0)
  const shownTimeOptions = timeOptions.includes(time) || !time ? timeOptions : [time, ...timeOptions]

  const submit = () => {
    if (isNewPatient && !newPatientName.trim()) {
      setLocalError('Введите ФИО нового пациента')
      return
    }
    const submissionProblem = checkPast ? validateBooking(checkedDraft, patients, services, practitioners, openedAt) : problem
    if (submissionProblem) {
      setLocalError(submissionProblem)
      return
    }
    setLocalError('')
    if (isNewPatient) onSaveNewPatient(draft, newPatientName.trim().replace(/\s+/g, ' '))
    else onSave(draft)
  }

  const shown = localError || error
  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>Новая запись <IconButton aria-label="Закрыть" onClick={onClose}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {shown && <Alert severity="error">{shown}</Alert>}
        {services.length === 0 && <Alert severity="warning">В справочнике нет включённых услуг. Администратор добавляет их на странице «График и цены».</Alert>}
        <TextField select label="Пациент" value={selectedPatientId} onChange={(event) => { setPatientId(event.target.value); setLocalError('') }}>
          {patients.map((patient) => <MenuItem key={patient.id} value={patient.id}>{patient.name}</MenuItem>)}
          <MenuItem value={NEW_PATIENT}><PersonAddRounded sx={{ mr: 1, verticalAlign: 'middle' }} />Новый пациент</MenuItem>
        </TextField>
        {isNewPatient && <><TextField required autoFocus label="ФИО нового пациента" value={newPatientName} onChange={(event) => setNewPatientName(event.target.value)} slotProps={{ htmlInput: { maxLength: 255 } }} /><Alert severity="info">Сейчас достаточно ФИО. После заполнения записи откроется полная карточка: заполните обязательные данные, затем пациент и запись будут сохранены.</Alert></>}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField label="Дата" type="date" value={date} onChange={(event) => setDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth /><TextField select label="Время" value={time} onChange={(event) => setTime(event.target.value)} helperText={timeOptions.length > 0 ? 'Доступное время с шагом 15 минут' : 'В смене врача нет времени для этой услуги'} fullWidth>{shownTimeOptions.map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</TextField></Stack>
        <TextField select label="Врач" value={selectedPractitionerId} onChange={(event) => setPractitionerId(event.target.value)}>{practitioners.map((doctor) => <MenuItem key={doctor.id} value={doctor.id}>{doctor.fullName} · {doctor.specialty.toLowerCase()}</MenuItem>)}</TextField>
        <TextField select label="Услуга" value={selectedServiceId} onChange={(event) => setServiceId(event.target.value)}>{services.map((item) => <MenuItem key={item.id} value={item.id}>{item.name} · {item.durationMinutes} мин · {priceText(item)}</MenuItem>)}</TextField>
        {!shown && problem === OUTSIDE_WORKING_HOURS && <Alert severity="warning">{OUTSIDE_WORKING_HOURS}</Alert>}
        {service?.priceMode === 'OPEN' && <TextField label="Оценка суммы, ₽ (необязательно)" value={estimate} onChange={(event) => setEstimate(event.target.value)} helperText={`Свободная цена: ${priceText(service).replace('свободная цена ', '')}. Точную сумму введут в кассе.`} />}
        {service && <Alert severity="info">{service.priceMode === 'OPEN' ? 'Свободная цена: сумму введут при выставлении счёта или оплате.' : `Стоимость ${priceText(service)} сохранится в записи.`} Если канал уведомлений подключён, пациент получит подтверждение и напоминание за сутки.</Alert>}
      </Stack>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" disabled={(!isNewPatient && !selectedPatientId) || (isNewPatient && !newPatientName.trim()) || !selectedPractitionerId || !selectedServiceId || !date || !time} onClick={submit}>{isNewPatient ? 'Далее: карточка пациента' : 'Создать запись'}</Button></DialogActions>
  </Dialog>
}
