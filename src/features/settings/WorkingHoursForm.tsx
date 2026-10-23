import { useState } from 'react'
import { Alert, Box, Button, Checkbox, FormControlLabel, TextField, Typography } from '@mui/material'
import type { Practitioner } from '../../api/types'
import { dayLabels, fromRows, toRows, type ShiftRow } from './shifts'

type WorkingHoursFormProps = {
  practitioner: Practitioner
  onSave: (shifts: Practitioner['workingHours']) => Promise<string | null>
}

/** Недельная смена врача. Снятая галочка — выходной: в этот день запись не принимается. */
export function WorkingHoursForm({ practitioner, onSave }: WorkingHoursFormProps) {
  const [rows, setRows] = useState<ShiftRow[]>(() => toRows(practitioner.workingHours))
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const change = (day: number, patch: Partial<ShiftRow>) => setRows((items) => items.map((row) => row.day === day ? { ...row, ...patch } : row))

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

  return (
    <Box className="panel">
      <Typography sx={{ fontWeight: 750 }}>{practitioner.fullName}</Typography>
      <Typography sx={{ fontSize: 13, color: 'text.secondary', mb: 1 }}>{practitioner.specialty} · время в шкале расписания (UTC)</Typography>
      {error && <Alert severity="error" sx={{ mb: 1 }}>{error}</Alert>}
      {rows.map((row) => (
        <Box className="shift-row" key={row.day}>
          <FormControlLabel sx={{ minWidth: 170 }} control={<Checkbox checked={row.enabled} onChange={(event) => change(row.day, { enabled: event.target.checked })} />} label={dayLabels[row.day]} />
          {row.enabled
            ? <>
              <TextField size="small" type="time" label="С" value={row.startsAt} onChange={(event) => change(row.day, { startsAt: event.target.value })} slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': `${practitioner.fullName}, ${dayLabels[row.day]}, начало` } }} />
              <TextField size="small" type="time" label="До" value={row.endsAt} onChange={(event) => change(row.day, { endsAt: event.target.value })} slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': `${practitioner.fullName}, ${dayLabels[row.day]}, конец` } }} />
            </>
            : <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>Выходной</Typography>}
        </Box>
      ))}
      <Button variant="contained" sx={{ mt: 1.5 }} disabled={busy} onClick={() => void save()} aria-label={`Сохранить график ${practitioner.fullName}`}>Сохранить график</Button>
    </Box>
  )
}
