import { useState } from 'react'
import { Box, Button, Chip, TextField, Typography } from '@mui/material'
import type { ServiceItem } from '../../api/types'
import { formatMoney } from '../schedule/booking'
import { minorToRubles, rublesToMinor } from './shifts'

type PriceFormProps = {
  service: ServiceItem
  onSave: (priceMinor: number) => Promise<string | null>
  /** Выключить или снова включить услугу для новых записей. */
  onToggle: () => Promise<string | null>
}

export function PriceForm({ service, onSave, onToggle }: PriceFormProps) {
  const [value, setValue] = useState(minorToRubles(service.priceMinor))
  const [error, setError] = useState('')
  const minor = rublesToMinor(value)

  const save = async () => {
    if (minor === null) {
      setError('Введите сумму в рублях, например 4500 или 4500,50')
      return
    }
    setError((await onSave(minor)) ?? '')
  }

  const toggle = async () => setError((await onToggle()) ?? '')

  return (
    <Box className="service-row" sx={{ opacity: service.active ? 1 : 0.75 }}>
      <Box sx={{ minWidth: 200 }}><Typography sx={{ fontWeight: 650 }}>{service.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{service.durationMinutes} мин · сейчас {formatMoney(service.priceMinor, service.currency)}</Typography>{!service.active && <Chip size="small" variant="outlined" label="Выключена: не предлагается в новой записи" sx={{ mt: 0.5 }} />}</Box>
      <TextField size="small" label="Цена, ₽" value={value} error={Boolean(error)} onChange={(event) => setValue(event.target.value)} slotProps={{ htmlInput: { 'aria-label': `Цена ${service.name}, рублей` } }} />
      <Button variant="outlined" disabled={minor === service.priceMinor} onClick={() => void save()} aria-label={`Сохранить цену ${service.name}`}>Сохранить</Button>
      <Button color={service.active ? 'warning' : 'primary'} onClick={() => void toggle()} aria-label={`${service.active ? 'Выключить' : 'Включить'} услугу ${service.name}`}>{service.active ? 'Выключить' : 'Включить'}</Button>
      {/* Ошибка — отдельной строкой под полями, чтобы кнопки не прыгали. */}
      {error && <Typography className="service-error" role="alert" sx={{ color: 'error.main', fontSize: 13 }}>{error}</Typography>}
    </Box>
  )
}
