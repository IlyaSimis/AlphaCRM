import { useState } from 'react'
import { Box, Button, Chip, MenuItem, TextField, Typography } from '@mui/material'
import type { ServicePricing } from '../../api/schedule'
import type { ServiceItem } from '../../api/types'
import { priceText } from '../schedule/booking'
import { parseOpenLimits } from './serviceDraft'
import { minorToRubles, rublesToMinor } from './shifts'

type PriceFormProps = {
  service: ServiceItem
  onSave: (pricing: ServicePricing) => Promise<string | null>
  /** Выключить или снова включить услугу для новых записей. */
  onToggle: () => Promise<string | null>
  /** false — только просмотр: цены меняют администраторы и владелец. */
  editable?: boolean
}

/**
 * Цена услуги: фиксированная сумма или свободная цена с пределами «от – до». Свободную сумму
 * вводят в кассе при выставлении счёта или оплате, в пределах услуги.
 */
export function PriceForm({ service, onSave, onToggle, editable = true }: PriceFormProps) {
  const open = service.priceMode === 'OPEN'
  const [mode, setMode] = useState<'FIXED' | 'OPEN'>(open ? 'OPEN' : 'FIXED')
  const [value, setValue] = useState(service.priceMinor == null ? '' : minorToRubles(service.priceMinor))
  const [min, setMin] = useState(service.minMinor == null ? '' : minorToRubles(service.minMinor))
  const [max, setMax] = useState(service.maxMinor == null ? '' : minorToRubles(service.maxMinor))
  const [error, setError] = useState('')
  const minor = rublesToMinor(value)
  const unchanged = mode === 'FIXED'
    ? !open && minor === service.priceMinor
    : open && rublesToMinor(min) === service.minMinor && rublesToMinor(max) === service.maxMinor

  const save = async () => {
    if (mode === 'OPEN') {
      const limits = parseOpenLimits(min, max)
      if ('error' in limits) {
        setError(limits.error)
        return
      }
      setError((await onSave({ priceMode: 'OPEN', ...limits })) ?? '')
      return
    }
    if (minor === null) {
      setError('Введите сумму в рублях, например 4500 или 4500,50')
      return
    }
    setError((await onSave({ priceMode: 'FIXED', priceMinor: minor })) ?? '')
  }

  const toggle = async () => setError((await onToggle()) ?? '')

  return (
    <Box className="service-row" sx={{ opacity: service.active ? 1 : 0.75 }}>
      <Box sx={{ minWidth: 200 }}><Typography sx={{ fontWeight: 650 }}>{service.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{service.durationMinutes} мин · сейчас {priceText(service)}</Typography>{!service.active && <Chip size="small" variant="outlined" label="Выключена: не предлагается в новой записи" sx={{ mt: 0.5 }} />}</Box>
      {editable && <>
        <TextField size="small" select label="Режим" value={mode} onChange={(event) => setMode(event.target.value as 'FIXED' | 'OPEN')} slotProps={{ htmlInput: { 'aria-label': `Режим цены ${service.name}` } }} sx={{ minWidth: 150 }}>
          <MenuItem value="FIXED">Фиксированная</MenuItem>
          <MenuItem value="OPEN">Свободная</MenuItem>
        </TextField>
        {mode === 'FIXED'
          ? <TextField size="small" label="Цена, ₽" value={value} error={Boolean(error)} onChange={(event) => setValue(event.target.value)} slotProps={{ htmlInput: { 'aria-label': `Цена ${service.name}, рублей` } }} />
          : <>
            <TextField size="small" label="От, ₽" value={min} error={Boolean(error)} onChange={(event) => setMin(event.target.value)} slotProps={{ htmlInput: { 'aria-label': `Нижний предел ${service.name}, рублей` } }} />
            <TextField size="small" label="До, ₽" value={max} error={Boolean(error)} onChange={(event) => setMax(event.target.value)} slotProps={{ htmlInput: { 'aria-label': `Верхний предел ${service.name}, рублей` } }} />
          </>}
        <Button variant="outlined" disabled={unchanged} onClick={() => void save()} aria-label={`Сохранить цену ${service.name}`}>Сохранить</Button>
        <Button color={service.active ? 'warning' : 'primary'} onClick={() => void toggle()} aria-label={`${service.active ? 'Выключить' : 'Включить'} услугу ${service.name}`}>{service.active ? 'Выключить' : 'Включить'}</Button>
      </>}
      {/* Ошибка — отдельной строкой под полями, чтобы кнопки не прыгали. */}
      {error && <Typography className="service-error" role="alert" sx={{ color: 'error.main', fontSize: 13 }}>{error}</Typography>}
    </Box>
  )
}
