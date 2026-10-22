import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import type { Practitioner, ScheduleItem } from '../../api/types'
import { OUTSIDE_WORKING_HOURS, fitsWorkingHours } from './workingHours'

export type RescheduleDraft = { practitionerId: string; date: string; time: string; minutes: number }

type RescheduleDialogProps = {
  item: ScheduleItem
  practitioners: Practitioner[]
  error: string
  onClose: () => void
  onSave: (draft: RescheduleDraft) => void
}

/** Перенос записи: новая дата, время и, при необходимости, другой врач. Длительность сохраняется. */
export function RescheduleDialog({ item, practitioners, error, onClose, onSave }: RescheduleDialogProps) {
  const [date, setDate] = useState(item.date)
  const [time, setTime] = useState(item.time)
  const [practitionerId, setPractitionerId] = useState(item.practitionerId)
  const [localError, setLocalError] = useState('')
  const practitioner = practitioners.find((doctor) => doctor.id === practitionerId)
  const outside = !fitsWorkingHours(practitioner, date, time, item.minutes)
  const unchanged = date === item.date && time === item.time && practitionerId === item.practitionerId

  const submit = () => {
    if (outside) {
      setLocalError(OUTSIDE_WORKING_HOURS)
      return
    }
    setLocalError('')
    onSave({ practitionerId, date, time, minutes: item.minutes })
  }

  const shown = localError || error
  return <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle>Перенос записи</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        <Typography sx={{ color: 'text.secondary' }}>{item.name} · {item.type.toLowerCase()} · {item.duration}</Typography>
        {shown && <Alert severity="error">{shown}</Alert>}
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
          <TextField label="Новая дата" type="date" value={date} onChange={(event) => setDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth />
          <TextField label="Новое время" type="time" value={time} onChange={(event) => setTime(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth />
        </Stack>
        <TextField select label="Врач" value={practitionerId} onChange={(event) => setPractitionerId(event.target.value)}>
          {practitioners.map((doctor) => <MenuItem key={doctor.id} value={doctor.id}>{doctor.fullName}</MenuItem>)}
        </TextField>
        {!shown && outside && <Alert severity="warning">{OUTSIDE_WORKING_HOURS}</Alert>}
        <Alert severity="info">Статус записи не меняется. Пациенту ничего не отправляется.</Alert>
      </Stack>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" disabled={!date || !time || unchanged} onClick={submit}>Перенести</Button></DialogActions>
  </Dialog>
}
