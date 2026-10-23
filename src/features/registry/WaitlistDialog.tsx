import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material'
import type { PatientItem, Practitioner, ServiceItem } from '../../api/types'
import { shiftDate } from '../../shared/dates'
import { bookableServices } from '../schedule/booking'
import type { WaitlistDraft } from './useRegistry'

type WaitlistDialogProps = {
  patients: PatientItem[]
  practitioners: Practitioner[]
  services: ServiceItem[]
  today: string
  error: string
  onClose: () => void
  onSave: (draft: WaitlistDraft) => void
}

export function WaitlistDialog({ patients, practitioners, services: catalog, today, error, onClose, onSave }: WaitlistDialogProps) {
  const services = bookableServices(catalog)
  const [patientId, setPatientId] = useState(patients[0]?.id ?? '')
  const [practitionerId, setPractitionerId] = useState(practitioners[0]?.id ?? '')
  const [serviceId, setServiceId] = useState(services[0]?.id ?? '')
  const [wishFrom, setWishFrom] = useState(today)
  const [wishTo, setWishTo] = useState(shiftDate(today, 14))
  return <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
    <DialogTitle>В лист ожидания</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField select label="Пациент" value={patientId} onChange={(event) => setPatientId(event.target.value)}>
          {patients.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
        </TextField>
        <TextField select label="Врач" value={practitionerId} onChange={(event) => setPractitionerId(event.target.value)}>
          {practitioners.map((item) => <MenuItem key={item.id} value={item.id}>{item.fullName}</MenuItem>)}
        </TextField>
        <TextField select label="Услуга" value={serviceId} onChange={(event) => setServiceId(event.target.value)}>
          {services.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
        </TextField>
        <Stack direction="row" spacing={2}>
          <TextField label="С" type="date" value={wishFrom} onChange={(event) => setWishFrom(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth />
          <TextField label="По" type="date" value={wishTo} onChange={(event) => setWishTo(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth />
        </Stack>
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={!patientId || !practitionerId || !serviceId || !wishFrom || !wishTo} onClick={() => onSave({ patientId, practitionerId, serviceId, wishFrom, wishTo })}>Добавить</Button>
    </DialogActions>
  </Dialog>
}
