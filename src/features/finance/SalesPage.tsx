import { useEffect, useState } from 'react'
import { InsightsRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, CircularProgress, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { loadSales } from '../../api/finance'
import type { SalesGroup, SalesReport } from '../../api/types'
import { Metric } from '../../shared/ui'
import { formatMoney } from '../schedule/booking'
import { groupLabels, lineLabel, periodOf, presetLabels, type PeriodPreset } from './finance'

/**
 * Продажи и выручка (раздел F, finance/sales): оплаты по дате оплаты, возвраты по дате возврата,
 * по дням, услугам или врачам. Только суммы — без пациентов; менеджер видит то же (агрегаты).
 */
export function SalesPage({ apiOnline }: { apiOnline: boolean }) {
  const [preset, setPreset] = useState<PeriodPreset>('month')
  const [period, setPeriod] = useState(() => periodOf('month'))
  const [group, setGroup] = useState<SalesGroup>('day')
  const [report, setReport] = useState<SalesReport | null>(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!apiOnline || !period.from || !period.to) return
    let cancelled = false
    setReport(null)
    loadSales(period.from, period.to, group)
      .then((result) => { if (!cancelled) { setReport(result); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline, period.from, period.to, group, revision])

  const choose = (next: PeriodPreset) => {
    setPreset(next)
    if (next !== 'custom') setPeriod(periodOf(next))
  }

  if (!apiOnline) return <Alert severity="info">Отчёт о продажах строится на сервере: в демо-песочнице оплат нет.</Alert>

  const currency = report?.currency ?? 'RUB'
  const totals = report?.totals
  return (
    <>
      <Box className="section-intro">
        <Box><Typography sx={{ color: 'text.secondary' }}>Оплаты минус возвраты за период. Пациентов в отчёте нет — только услуги, врачи и суммы.</Typography></Box>
        <Button variant="outlined" startIcon={<RefreshRounded />} onClick={() => setRevision((value) => value + 1)}>Обновить</Button>
      </Box>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5} sx={{ mb: 2, alignItems: { md: 'center' } }}>
        <ToggleButtonGroup exclusive size="small" value={preset} onChange={(_, next: PeriodPreset | null) => { if (next) choose(next) }} aria-label="Период">
          {(Object.keys(presetLabels) as PeriodPreset[]).map((item) => <ToggleButton key={item} value={item}>{presetLabels[item]}</ToggleButton>)}
        </ToggleButtonGroup>
        {preset === 'custom' && <>
          <TextField size="small" type="date" label="С" value={period.from} onChange={(event) => setPeriod((current) => ({ ...current, from: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} />
          <TextField size="small" type="date" label="По" value={period.to} onChange={(event) => setPeriod((current) => ({ ...current, to: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} />
        </>}
        <Box sx={{ flex: 1 }} />
        <ToggleButtonGroup exclusive size="small" value={group} onChange={(_, next: SalesGroup | null) => { if (next) setGroup(next) }} aria-label="Группировка">
          {(Object.keys(groupLabels) as SalesGroup[]).map((item) => <ToggleButton key={item} value={item}>{groupLabels[item]}</ToggleButton>)}
        </ToggleButtonGroup>
      </Stack>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      {totals && <Box className="summary-grid" sx={{ mb: 2 }}>
        <Metric value={formatMoney(totals.netMinor, currency)} label="выручка" note="оплаты минус возвраты" accent />
        <Metric value={formatMoney(totals.amountMinor, currency)} label="оплачено" note={`оплат: ${totals.payments}`} />
        <Metric value={formatMoney(totals.refundedMinor, currency)} label="возвраты" note={`возвратов: ${totals.refunds}`} />
        <Metric value={formatMoney(totals.openPriceMinor, currency)} label="свободная цена" note="часть оплат" />
      </Box>}
      <Box className="panel audit-panel">
        {!report && !error ? <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress aria-label="Загрузка отчёта" /></Box>
          : report && report.lines.length === 0 ? <Box className="empty" sx={{ minHeight: 160 }}><InsightsRounded /><Typography sx={{ fontWeight: 700 }}>За период оплат нет</Typography></Box>
          : report && <table className="audit-table sales-table">
            <thead><tr><th>{group === 'day' ? 'День' : group === 'service' ? 'Услуга' : 'Врач'}</th><th>Оплат</th><th>Оплачено</th><th>Возвраты</th><th>Выручка</th></tr></thead>
            <tbody>
              {report.lines.map((line) => (
                <tr key={line.key}>
                  <td>{lineLabel(line, group)}</td>
                  <td>{line.payments}</td>
                  <td className="nowrap">{formatMoney(line.amountMinor, currency)}</td>
                  <td className="nowrap">{line.refundedMinor > 0 ? `−${formatMoney(line.refundedMinor, currency)}` : '—'}</td>
                  <td className="nowrap" style={{ fontWeight: 700 }}>{formatMoney(line.netMinor, currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>}
      </Box>
    </>
  )
}
