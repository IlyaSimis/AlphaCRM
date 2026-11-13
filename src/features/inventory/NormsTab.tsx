import { useEffect, useState } from 'react'
import { DeleteOutlineRounded } from '@mui/icons-material'
import { Alert, Box, Button, IconButton, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { loadItems } from '../../api/inventory'
import { loadServiceNorms, saveServiceNorms } from '../../api/schedule'
import type { InventoryItem, ServiceItem } from '../../api/types'
import { formatQuantity, parseQuantity } from './inventory'

type NormsTabProps = { services: ServiceItem[]; canEdit: boolean; notify: (message: string) => void }

type NormDraft = { key: string; itemId: string; quantity: string }

/**
 * Нормы расхода услуги (сценарий D7): при завершении приёма склад списывает эти позиции с
 * основного склада. Немаркируемые — сразу, маркируемые — черновиком расхода для сканирования марок.
 */
export function NormsTab({ services, canEdit, notify }: NormsTabProps) {
  const active = services.filter((service) => service.active)
  const [serviceId, setServiceId] = useState(active[0]?.id ?? '')
  const [items, setItems] = useState<InventoryItem[]>([])
  const [norms, setNorms] = useState<NormDraft[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    loadItems({ sort: 'name' }).then(setItems).catch((reason: Error) => setError(reason.message))
  }, [])

  useEffect(() => {
    if (!serviceId) return
    let cancelled = false
    loadServiceNorms(serviceId)
      .then((rows) => { if (!cancelled) { setNorms(rows.map((row) => ({ key: crypto.randomUUID(), itemId: row.itemId, quantity: formatQuantity(row.quantity) }))); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [serviceId])

  const save = async () => {
    const parsed: Array<{ itemId: string; quantity: number }> = []
    for (const norm of norms) {
      if (!norm.itemId) continue
      const quantity = parseQuantity(norm.quantity)
      if (!quantity) {
        setError('Количество — положительное число, например 1 или 0,5')
        return
      }
      parsed.push({ itemId: norm.itemId, quantity })
    }
    if (new Set(parsed.map((norm) => norm.itemId)).size !== parsed.length) {
      setError('Каждая позиция — в нормах один раз')
      return
    }
    setBusy(true)
    try {
      await saveServiceNorms(serviceId, parsed)
      notify('Нормы расхода сохранены')
      setError('')
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const patch = (key: string, change: Partial<NormDraft>) => setNorms((current) => current.map((norm) => norm.key === key ? { ...norm, ...change } : norm))

  return (
    <Box className="panel">
      <Typography sx={{ color: 'text.secondary', mb: 2 }}>При завершении приёма склад спишет эти позиции по норме услуги. Нехватка не блокирует приём — регистратура получит задачу.</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <TextField select label="Услуга" value={serviceId} onChange={(event) => setServiceId(event.target.value)} sx={{ minWidth: 280, mb: 2 }}>
        {active.map((service) => <MenuItem key={service.id} value={service.id}>{service.name}</MenuItem>)}
      </TextField>
      <Stack spacing={1.5}>
        {norms.length === 0 && <Typography sx={{ color: 'text.secondary' }}>Норм нет: приём со склада ничего не списывает.</Typography>}
        {norms.map((norm) => {
          const item = items.find((option) => option.id === norm.itemId)
          return (
            <Stack key={norm.key} direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
              <TextField select size="small" label="Позиция" value={norm.itemId} disabled={!canEdit} onChange={(event) => patch(norm.key, { itemId: event.target.value })} sx={{ minWidth: 260 }}>
                {items.map((option) => <MenuItem key={option.id} value={option.id}>{option.name}{option.marked ? ' · марк.' : ''}</MenuItem>)}
              </TextField>
              <TextField size="small" label={item ? `На приём, ${item.unit}` : 'На приём'} value={norm.quantity} disabled={!canEdit} onChange={(event) => patch(norm.key, { quantity: event.target.value })} sx={{ width: 150 }} />
              {canEdit && <IconButton aria-label="Убрать норму" onClick={() => setNorms((current) => current.filter((row) => row.key !== norm.key))}><DeleteOutlineRounded /></IconButton>}
            </Stack>
          )
        })}
      </Stack>
      {canEdit && <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
        <Button disabled={!serviceId || items.length === 0} onClick={() => setNorms((current) => [...current, { key: crypto.randomUUID(), itemId: '', quantity: '1' }])}>Добавить позицию</Button>
        <Button variant="contained" disabled={!serviceId || busy} onClick={() => void save()}>Сохранить нормы</Button>
      </Stack>}
    </Box>
  )
}
