import { useState } from 'react'
import { Box, Button, MenuItem, TextField, Typography } from '@mui/material'
import type { ServiceItem } from '../../api/types'
import type { NewService } from '../../app/useClinicData'
import { parseServiceDraft, type ServiceDraft } from './serviceDraft'

type ServiceFormProps = {
  services: ServiceItem[]
  onCreate: (input: NewService) => Promise<string | null>
}

const empty: ServiceDraft = { name: '', minutes: '', price: '', mode: 'FIXED', min: '', max: '' }

/** Новая услуга справочника: сразу появляется в записи на приём. */
export function ServiceForm({ services, onCreate }: ServiceFormProps) {
  const [draft, setDraft] = useState<ServiceDraft>(empty)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const set = (patch: Partial<ServiceDraft>) => setDraft((current) => ({ ...current, ...patch }))

  const create = async () => {
    const parsed = parseServiceDraft(draft, services)
    if ('error' in parsed) {
      setError(parsed.error)
      return
    }
    setBusy(true)
    const problem = await onCreate(parsed.value)
    setBusy(false)
    setError(problem ?? '')
    if (!problem) setDraft(empty)
  }

  return (
    <Box className="service-form">
      <TextField size="small" label="Название услуги" value={draft.name} onChange={(event) => set({ name: event.target.value })} />
      <TextField size="small" label="Длительность, мин" value={draft.minutes} onChange={(event) => set({ minutes: event.target.value })} slotProps={{ htmlInput: { inputMode: 'numeric' } }} />
      <TextField size="small" select label="Цена" value={draft.mode ?? 'FIXED'} onChange={(event) => set({ mode: event.target.value as 'FIXED' | 'OPEN' })} slotProps={{ htmlInput: { 'aria-label': 'Режим цены новой услуги' } }}>
        <MenuItem value="FIXED">Фиксированная</MenuItem>
        <MenuItem value="OPEN">Свободная</MenuItem>
      </TextField>
      {draft.mode === 'OPEN'
        ? <>
          <TextField size="small" label="От, ₽" value={draft.min} onChange={(event) => set({ min: event.target.value })} error={Boolean(error)} />
          <TextField size="small" label="До, ₽" value={draft.max} onChange={(event) => set({ max: event.target.value })} error={Boolean(error)} />
        </>
        : <TextField size="small" label="Цена новой услуги, ₽" value={draft.price} onChange={(event) => set({ price: event.target.value })} error={Boolean(error)} />}
      <Button variant="contained" disabled={busy} onClick={() => void create()}>Добавить услугу</Button>
      {error && <Typography className="service-error" role="alert" sx={{ color: 'error.main', fontSize: 13 }}>{error}</Typography>}
    </Box>
  )
}
