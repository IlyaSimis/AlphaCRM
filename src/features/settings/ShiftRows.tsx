import { Box, Checkbox, FormControlLabel, TextField, Typography } from '@mui/material'
import { dayLabels, type ShiftRow } from './shifts'

type ShiftRowsProps = {
  rows: ShiftRow[]
  /** Начало подписи полей для экранного диктора: имя врача или «Новый врач». */
  label: string
  onChange: (day: number, patch: Partial<ShiftRow>) => void
}

/** Семь дней недели: снятая галочка — выходной, у рабочего дня — начало и конец смены. */
export function ShiftRows({ rows, label, onChange }: ShiftRowsProps) {
  return <>
    {rows.map((row) => (
      <Box className="shift-row" key={row.day}>
        <FormControlLabel sx={{ minWidth: 170 }} control={<Checkbox checked={row.enabled} onChange={(event) => onChange(row.day, { enabled: event.target.checked })} />} label={dayLabels[row.day]} />
        {row.enabled
          ? <>
            <TextField size="small" type="time" label="С" value={row.startsAt} onChange={(event) => onChange(row.day, { startsAt: event.target.value })} slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': `${label}, ${dayLabels[row.day]}, начало` } }} />
            <TextField size="small" type="time" label="До" value={row.endsAt} onChange={(event) => onChange(row.day, { endsAt: event.target.value })} slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'aria-label': `${label}, ${dayLabels[row.day]}, конец` } }} />
          </>
          : <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>Выходной</Typography>}
      </Box>
    ))}
  </>
}
