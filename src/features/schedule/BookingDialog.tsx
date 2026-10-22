import { useState } from 'react'
import { CloseRounded } from '@mui/icons-material'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, Stack, TextField } from '@mui/material'
import type { PatientItem, Practitioner, ServiceItem } from '../../api/types'
import { formatMoney, validateBooking, type BookingDraft } from './booking'
import { OUTSIDE_WORKING_HOURS } from './workingHours'

type BookingDialogProps = {
  open: boolean
  patients: PatientItem[]
  practitioners: Practitioner[]
  services: ServiceItem[]
  defaultPractitionerId: string
  error: string
  initialDate: string
  onClose: () => void
  onSave: (draft: BookingDraft) => void
}

export function BookingDialog({ open, patients, practitioners, services, defaultPractitionerId, error, initialDate, onClose, onSave }: BookingDialogProps) {
  const fallbackPatientId = patients.find((item) => item.name === 'Анна Михайлова')?.id ?? patients[0]?.id ?? ''
  const fallbackServiceId = services.find((item) => item.name === 'Повторный приём')?.id ?? services[0]?.id ?? ''
  const [patientId, setPatientId] = useState(fallbackPatientId)
  const [date, setDate] = useState(initialDate)
  const [time, setTime] = useState('13:00')
  const [practitionerId, setPractitionerId] = useState(defaultPractitionerId)
  const [serviceId, setServiceId] = useState(fallbackServiceId)
  const [localError, setLocalError] = useState('')
  const selectedPatientId = patients.some((item) => item.id === patientId) ? patientId : fallbackPatientId
  const selectedPractitionerId = practitioners.some((item) => item.id === practitionerId) ? practitionerId : practitioners[0]?.id ?? ''
  const selectedServiceId = services.some((item) => item.id === serviceId) ? serviceId : fallbackServiceId
  const draft: BookingDraft = { patientId: selectedPatientId, date, time, serviceId: selectedServiceId, practitionerId: selectedPractitionerId }
  const problem = validateBooking(draft, patients, services, practitioners)
  const service = services.find((item) => item.id === selectedServiceId)

  const submit = () => {
    // Ошибку, которую можно найти без сервера, показываем сразу и не отправляем запрос.
    if (problem) {
      setLocalError(problem)
      return
    }
    setLocalError('')
    onSave(draft)
  }

  const shown = localError || error
  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>Новая запись <IconButton onClick={onClose}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {shown && <Alert severity="error">{shown}</Alert>}
        <TextField select label="Пациент" value={selectedPatientId} onChange={(event) => setPatientId(event.target.value)}>
          {patients.map((patient) => <MenuItem key={patient.id} value={patient.id}>{patient.name}</MenuItem>)}
        </TextField>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField label="Дата" type="date" value={date} onChange={(event) => setDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth /><TextField label="Время" type="time" value={time} onChange={(event) => setTime(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth /></Stack>
        <TextField select label="Врач" value={selectedPractitionerId} onChange={(event) => setPractitionerId(event.target.value)}>
          {practitioners.map((doctor) => <MenuItem key={doctor.id} value={doctor.id}>{doctor.fullName} · {doctor.specialty.toLowerCase()}</MenuItem>)}
        </TextField>
        <TextField select label="Услуга" value={selectedServiceId} onChange={(event) => setServiceId(event.target.value)}>
          {services.map((item) => <MenuItem key={item.id} value={item.id}>{item.name} · {item.durationMinutes} мин · {formatMoney(item.priceMinor, item.currency)}</MenuItem>)}
        </TextField>
        {!shown && problem === OUTSIDE_WORKING_HOURS && <Alert severity="warning">{OUTSIDE_WORKING_HOURS}</Alert>}
        {service && <Alert severity="info">Стоимость {formatMoney(service.priceMinor, service.currency)} сохранится в записи. Пациенту ничего не отправляется.</Alert>}
      </Stack>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" disabled={!selectedPatientId || !selectedPractitionerId || !selectedServiceId || !date || !time} onClick={submit}>Создать запись</Button></DialogActions>
  </Dialog>
}
