import { useState } from 'react'
import { Box, Button, TextField, Typography } from '@mui/material'
import type { ServiceItem } from '../../api/types'
import { parseServiceDraft, type ServiceDraft } from './serviceDraft'

type ServiceFormProps = {
  services: ServiceItem[]
  onCreate: (input: { name: string; durationMinutes: number; priceMinor: number }) => Promise<string | null>
}

const empty: ServiceDraft = { name: '', minutes: '', price: '' }

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
      <TextField size="small" label="Цена новой услуги, ₽" value={draft.price} onChange={(event) => set({ price: event.target.value })} error={Boolean(error)} />
      <Button variant="contained" disabled={busy} onClick={() => void create()}>Добавить услугу</Button>
      {error && <Typography className="service-error" role="alert" sx={{ color: 'error.main', fontSize: 13 }}>{error}</Typography>}
    </Box>
  )
}
