import { useState } from 'react'
import { Alert, Box, Button, TextField, Typography } from '@mui/material'
import type { Practitioner } from '../../api/types'
import { clinicTimeZone } from '../../shared/clinicTime'
import { specialtyAndRoom } from '../../shared/practitioners'
import { ShiftRows } from './ShiftRows'
import { fromRows, toRows, type ShiftRow } from './shifts'

type WorkingHoursFormProps = {
  practitioner: Practitioner
  onSave: (shifts: Practitioner['workingHours']) => Promise<string | null>
  onSaveCard: (input: { specialty: string; room: string }) => Promise<string | null>
}

/**
 * Карточка врача на «График и цены»: специальность и кабинет, ниже — недельная смена. Снятая
 * галочка — выходной: в этот день запись не принимается.
 */
export function WorkingHoursForm({ practitioner, onSave, onSaveCard }: WorkingHoursFormProps) {
  const [rows, setRows] = useState<ShiftRow[]>(() => toRows(practitioner.workingHours))
  const [specialty, setSpecialty] = useState(practitioner.specialty)
  const [room, setRoom] = useState(practitioner.room ?? '')
  const [error, setError] = useState('')
  const [cardError, setCardError] = useState('')
  const [busy, setBusy] = useState(false)
  const change = (day: number, patch: Partial<ShiftRow>) => setRows((items) => items.map((row) => row.day === day ? { ...row, ...patch } : row))
  const cardChanged = specialty.trim() !== practitioner.specialty || room.trim() !== (practitioner.room ?? '')

  const save = async () => {
    const result = fromRows(rows)
    if ('error' in result) {
      setError(result.error)
      return
    }
    setBusy(true)
    setError((await onSave(result.shifts)) ?? '')
    setBusy(false)
  }

  const saveCard = async () => {
    if (!specialty.trim()) {
      setCardError('Укажите специальность')
      return
    }
    setBusy(true)
    setCardError((await onSaveCard({ specialty: specialty.trim(), room: room.trim() })) ?? '')
    setBusy(false)
  }

  return (
    <Box className="panel">
      <Typography sx={{ fontWeight: 750 }}>{practitioner.fullName}</Typography>
      <Typography sx={{ fontSize: 13, color: 'text.secondary', mb: 1 }}>{specialtyAndRoom(practitioner)} · время клиники ({clinicTimeZone()})</Typography>
      <Box className="practitioner-card-row">
        <TextField size="small" label="Специальность" value={specialty} onChange={(event) => setSpecialty(event.target.value)} slotProps={{ htmlInput: { 'aria-label': `${practitioner.fullName}, специальность`, maxLength: 120 } }} />
        <TextField size="small" label="Кабинет" value={room} onChange={(event) => setRoom(event.target.value)} slotProps={{ htmlInput: { 'aria-label': `${practitioner.fullName}, кабинет`, maxLength: 80 } }} />
        <Button variant="outlined" disabled={busy || !cardChanged} onClick={() => void saveCard()} aria-label={`Сохранить карточку ${practitioner.fullName}`}>Сохранить</Button>
      </Box>
      {cardError && <Typography role="alert" sx={{ color: 'error.main', fontSize: 13, mb: 1 }}>{cardError}</Typography>}
      {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
      <ShiftRows rows={rows} label={practitioner.fullName} onChange={change} />
      <Button variant="contained" sx={{ mt: 1.5 }} disabled={busy} onClick={() => void save()} aria-label={`Сохранить график ${practitioner.fullName}`}>Сохранить график</Button>
    </Box>
  )
}
