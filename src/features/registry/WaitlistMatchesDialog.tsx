import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import type { PatientItem, ScheduleItem, ServiceItem, WaitlistItem } from '../../api/types'
import { periodLabel } from './registry'

type WaitlistMatchesDialogProps = {
  slot: ScheduleItem
  matches: WaitlistItem[]
  patients: PatientItem[]
  services: ServiceItem[]
  error: string
  onClose: () => void
  onBook: (entry: WaitlistItem) => void
}

/** Освободившийся слот и ожидающие этого врача в этот день. Запись создаётся обычным способом. */
export function WaitlistMatchesDialog({ slot, matches, patients, services, error, onClose, onBook }: WaitlistMatchesDialogProps) {
  const patientName = (id: string) => patients.find((item) => item.id === id)?.name ?? 'Пациент'
  const serviceName = (id: string) => services.find((item) => item.id === id)?.name ?? 'Услуга'
  return <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle>Свободный слот {slot.time}: лист ожидания</DialogTitle>
    <DialogContent>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {matches.length === 0 && <Typography sx={{ color: 'text.secondary' }}>Подходящих ожидающих нет</Typography>}
      {matches.map((entry) => (
        <Box className="history-item" key={entry.id}>
          <Box>
            <Typography sx={{ fontWeight: 650 }}>{patientName(entry.patientId)}</Typography>
            <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{serviceName(entry.serviceId)} · ждёт {periodLabel(entry.wishFrom, entry.wishTo)}</Typography>
          </Box>
          <Button size="small" variant="contained" aria-label={`Записать ${patientName(entry.patientId)} на ${slot.time}`} onClick={() => onBook(entry)}>Записать на {slot.time}</Button>
        </Box>
      ))}
      <Alert severity="info" sx={{ mt: 2 }}>Запись создаётся как обычная. Пациенту ничего не отправляется — позвоните ему сами.</Alert>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Закрыть</Button></DialogActions>
  </Dialog>
}
