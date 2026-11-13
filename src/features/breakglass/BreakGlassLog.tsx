import { useEffect, useState } from 'react'
import { HealthAndSafetyRounded } from '@mui/icons-material'
import { Alert, Box, Chip, CircularProgress, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { loadBreakGlassLog } from '../../api/clinical'
import type { BreakGlassGrant } from '../../api/types'
import { clinicTimeZone } from '../../shared/clinicTime'
import { periodOf, presetLabels, type PeriodPreset } from '../finance/finance'
import { breakGlassReasonLabels } from './breakGlass'

function dateTime(iso?: string | null) {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: clinicTimeZone() })
}

/** Журнал экстренных доступов владельца (K1): кто, к какому пациенту, когда и почему. */
export function BreakGlassLog({ apiOnline }: { apiOnline: boolean }) {
  const [preset, setPreset] = useState<PeriodPreset>('month')
  const [period, setPeriod] = useState(() => periodOf('month'))
  const [rows, setRows] = useState<BreakGlassGrant[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!apiOnline || !period.from || !period.to) return
    let cancelled = false
    setRows(null)
    loadBreakGlassLog(period.from, period.to)
      .then((result) => { if (!cancelled) { setRows(result); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline, period.from, period.to])

  if (!apiOnline) return <Alert severity="info">Журнал хранится на сервере: в демо-песочнице экстренных доступов нет.</Alert>

  return (
    <>
      <Box className="section-intro"><Box><Typography sx={{ color: 'text.secondary' }}>Каждый экстренный доступ владельца к медицинским данным пациента: причина, пояснение и срок. Сам просмотр журнала тоже пишется в аудит.</Typography></Box></Box>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <ToggleButtonGroup exclusive size="small" value={preset} onChange={(_, next: PeriodPreset | null) => { if (next) { setPreset(next); if (next !== 'custom') setPeriod(periodOf(next)) } }} aria-label="Период">
          {(Object.keys(presetLabels) as PeriodPreset[]).map((item) => <ToggleButton key={item} value={item}>{presetLabels[item]}</ToggleButton>)}
        </ToggleButtonGroup>
        {preset === 'custom' && <>
          <TextField size="small" type="date" label="С" value={period.from} onChange={(event) => setPeriod((current) => ({ ...current, from: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField size="small" type="date" label="По" value={period.to} onChange={(event) => setPeriod((current) => ({ ...current, to: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} />
        </>}
      </Stack>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box className="panel audit-panel">
        {rows === null && !error ? <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress aria-label="Загрузка журнала" /></Box>
          : rows && rows.length === 0 ? <Box className="empty" sx={{ minHeight: 160 }}><HealthAndSafetyRounded /><Typography sx={{ fontWeight: 700 }}>За период экстренных доступов не было</Typography></Box>
          : rows && <table className="audit-table">
            <thead><tr><th>Когда</th><th>Кто</th><th>Пациент</th><th>Причина</th><th>Пояснение</th><th>Срок</th></tr></thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.grantId ?? row.grantedAt}>
                  <td className="nowrap">{dateTime(row.grantedAt)}</td>
                  <td>{row.ownerName ?? '—'}</td>
                  <td>{row.patientName ?? '—'}</td>
                  <td>{row.reasonCode ? breakGlassReasonLabels[row.reasonCode] ?? row.reasonCode : '—'}</td>
                  <td>{row.reasonText ?? '—'}</td>
                  <td className="nowrap">{row.active ? <Chip size="small" color="error" variant="outlined" label={`действует до ${dateTime(row.expiresAt).slice(-5)}`} /> : `до ${dateTime(row.expiresAt)}`}</td>
                </tr>
              ))}
            </tbody>
          </table>}
      </Box>
    </>
  )
}
