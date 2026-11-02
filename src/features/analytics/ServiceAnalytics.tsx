import { useEffect, useState } from 'react'
import { CloseRounded, DownloadRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, CircularProgress, Drawer, IconButton, Stack, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { loadServiceAnalytics, loadServiceAppointments } from '../../api/analytics'
import { statusLabel } from '../../api/schedule'
import type { ServiceAnalyticsReport, ServiceAnalyticsRow, ServiceAppointmentDetails } from '../../api/types'
import { clinicParts } from '../../shared/clinicTime'
import { shortDate } from '../../shared/dates'
import { Metric } from '../../shared/ui'
import { formatMoney } from '../schedule/booking'
import { rate } from './stats'

type DetailsFilter = 'ALL' | 'COMPLETED' | 'NO_SHOW' | 'WITHOUT_NEXT'

type ServiceAnalyticsProps = {
  apiOnline: boolean
  from: string
  to: string
  practitionerId?: string
  onOpenPatient: (patientId: string) => void
}

function percent(value: number | null) {
  return value === null ? '—' : `${value}%`
}

function delta(current: number, previous: number) {
  if (previous === 0) return current === 0 ? '0%' : 'первые данные'
  const value = Math.round(((current - previous) / Math.abs(previous)) * 100)
  return `${value > 0 ? '+' : ''}${value}% к прошлому периоду`
}

function money(value: number | null, currency: string) {
  return value === null ? '—' : formatMoney(value, currency)
}

function csvCell(value: string | number) {
  const text = String(value)
  return `"${text.replaceAll('"', '""')}"`
}

export function serviceAnalyticsCsv(report: ServiceAnalyticsReport) {
  const header = ['Услуга', 'Записи', 'Визиты', 'Пациенты', 'Новые', 'Повторные', 'No-show', 'Отмены', 'Без следующей записи', 'Выручка, коп.', 'Средний чек, коп.']
  const rows = report.rows.map((row) => [row.serviceName, row.appointments, row.visits, row.uniquePatients, row.newPatients, row.returningPatients, row.noShow, row.cancelled, row.withoutNextVisit, row.revenueMinor, row.averageCheckMinor ?? ''])
  return `\uFEFF${[header, ...rows].map((row) => row.map(csvCell).join(';')).join('\r\n')}`
}

export function ServiceAnalytics({ apiOnline, from, to, practitionerId, onOpenPatient }: ServiceAnalyticsProps) {
  const [report, setReport] = useState<ServiceAnalyticsReport | null>(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<ServiceAnalyticsRow | null>(null)
  const [details, setDetails] = useState<ServiceAppointmentDetails | null>(null)
  const [detailsError, setDetailsError] = useState('')
  const [detailsFilter, setDetailsFilter] = useState<DetailsFilter>('ALL')

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    setReport(null)
    setError('')
    loadServiceAnalytics(from, to, practitionerId)
      .then((result) => { if (!cancelled) setReport(result) })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline, from, to, practitionerId, revision])

  useEffect(() => {
    if (!selected || !apiOnline) return
    let cancelled = false
    setDetails(null)
    setDetailsError('')
    setDetailsFilter('ALL')
    loadServiceAppointments(from, to, selected.serviceKey, practitionerId)
      .then((result) => { if (!cancelled) setDetails(result) })
      .catch((reason: Error) => { if (!cancelled) setDetailsError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline, from, to, practitionerId, selected])

  if (!apiOnline) return <Alert severity="info">Отчёт по услугам строится на сервере: в демо-песочнице оплат и полной истории нет.</Alert>

  const currency = report?.currency ?? 'RUB'
  const totals = report?.totals
  const download = () => {
    if (!report) return
    const url = URL.createObjectURL(new Blob([serviceAnalyticsCsv(report)], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `services-${from}-${to}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }
  const appointments = details?.appointments ?? []
  const visibleAppointments = appointments.filter((appointment) => {
    if (detailsFilter === 'COMPLETED') return appointment.status === 'COMPLETED'
    if (detailsFilter === 'NO_SHOW') return appointment.status === 'NO_SHOW'
    if (detailsFilter === 'WITHOUT_NEXT') return appointment.withoutNextVisit
    return true
  })

  return <>
    <Box className="section-intro" sx={{ mb: 2 }}>
      <Typography sx={{ color: 'text.secondary' }}>Визиты считаются по дате приёма, выручка — по дате оплаты и возврата. Сравнение: {report ? `${shortDate(report.previousFrom)}–${shortDate(report.previousTo)}` : 'с предыдущим периодом'}.</Typography>
      <Stack direction="row" spacing={1}>
        <IconButton aria-label="Обновить отчёт по услугам" onClick={() => setRevision((value) => value + 1)}><RefreshRounded /></IconButton>
        <Button variant="outlined" startIcon={<DownloadRounded />} disabled={!report || report.rows.length === 0} onClick={download}>Экспорт CSV</Button>
      </Stack>
    </Box>
    {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
    {!report && !error && <Box className="empty" sx={{ minHeight: 180 }}><CircularProgress aria-label="Загрузка отчёта по услугам" /></Box>}
    {report && totals && <>
      <Box className="summary-grid analytics-kpis">
        <Metric value={String(totals.visits)} label="завершённые визиты" note={delta(totals.visits, report.previousTotals.visits)} />
        <Metric value={String(totals.uniquePatients)} label="уникальные пациенты" note={`${totals.newPatients} новых · ${totals.returningPatients} повторных`} />
        <Metric value={formatMoney(totals.revenueMinor, currency)} label="выручка" note={delta(totals.revenueMinor, report.previousTotals.revenueMinor)} />
        <Metric value={money(totals.averageCheckMinor, currency)} label="средний чек" note={`${totals.payments} оплат`} />
        <Metric value={percent(rate(totals.noShow, totals.visits + totals.noShow))} label="no-show" note={`${totals.noShow} из ${totals.visits + totals.noShow}`} accent={(rate(totals.noShow, totals.visits + totals.noShow) ?? 0) >= 20} />
        <Metric value={String(totals.withoutNextVisit)} label="без следующей записи" note="уникальные пациенты" accent={totals.withoutNextVisit > 0} />
      </Box>
      <Box className="panel audit-panel">
        {report.rows.length === 0 ? <Box className="empty" sx={{ minHeight: 150 }}><Typography sx={{ fontWeight: 700 }}>За выбранный период данных нет</Typography></Box> : <table className="audit-table service-analytics-table">
          <thead><tr><th>Услуга</th><th>Записи</th><th>Визиты</th><th>Пациенты</th><th>Новые / повторные</th><th>No-show</th><th>Без след. записи</th><th>Выручка</th><th>Средний чек</th></tr></thead>
          <tbody>{report.rows.map((row) => <tr key={row.serviceKey}>
            <td><Button variant="text" sx={{ p: 0, minWidth: 0, fontWeight: 700, textTransform: 'none' }} aria-label={`Открыть приёмы: ${row.serviceName}`} onClick={() => setSelected(row)}>{row.serviceName}</Button></td>
            <td>{row.appointments}</td>
            <td>{row.visits}<Typography component="span" sx={{ display: 'block', fontSize: 11, color: 'text.secondary' }}>{delta(row.visits, row.previousVisits)}</Typography></td>
            <td>{row.uniquePatients}</td>
            <td>{row.newPatients} / {row.returningPatients}</td>
            <td>{row.noShow}<Typography component="span" sx={{ display: 'block', fontSize: 11, color: 'text.secondary' }}>{percent(rate(row.noShow, row.visits + row.noShow))}</Typography></td>
            <td>{row.withoutNextVisit}</td>
            <td className="nowrap" style={{ fontWeight: 700 }}>{formatMoney(row.revenueMinor, currency)}<Typography component="span" sx={{ display: 'block', fontSize: 11, color: 'text.secondary', fontWeight: 400 }}>{delta(row.revenueMinor, row.previousRevenueMinor)}</Typography></td>
            <td className="nowrap">{money(row.averageCheckMinor, currency)}</td>
          </tr>)}</tbody>
        </table>}
      </Box>
    </>}
    <Drawer anchor="right" open={Boolean(selected)} onClose={() => setSelected(null)}>
      <Box className="service-details" role="dialog" aria-label={selected ? `Приёмы по услуге ${selected.serviceName}` : 'Приёмы по услуге'}>
        <Box className="panel-title-row">
          <Box><Typography variant="h5" sx={{ fontWeight: 750 }}>{selected?.serviceName}</Typography><Typography sx={{ color: 'text.secondary' }}>Исходные записи за {shortDate(from)}–{shortDate(to)}</Typography></Box>
          <IconButton aria-label="Закрыть детализацию" onClick={() => setSelected(null)}><CloseRounded /></IconButton>
        </Box>
        <ToggleButtonGroup exclusive size="small" value={detailsFilter} onChange={(_, next: DetailsFilter | null) => { if (next) setDetailsFilter(next) }} aria-label="Фильтр приёмов" sx={{ my: 2, flexWrap: 'wrap' }}>
          <ToggleButton value="ALL">Все · {appointments.length}</ToggleButton>
          <ToggleButton value="COMPLETED">Завершённые</ToggleButton>
          <ToggleButton value="NO_SHOW">Неявки</ToggleButton>
          <ToggleButton value="WITHOUT_NEXT">Без следующей записи</ToggleButton>
        </ToggleButtonGroup>
        {detailsError && <Alert severity="error">{detailsError}</Alert>}
        {!details && !detailsError && <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress aria-label="Загрузка приёмов по услуге" /></Box>}
        {details && visibleAppointments.length === 0 && <Typography sx={{ color: 'text.secondary' }}>В выбранном срезе приёмов нет.</Typography>}
        {visibleAppointments.length > 0 && <Box sx={{ overflowX: 'auto' }}><table className="audit-table service-details-table">
          <thead><tr><th>Дата и время</th><th>Пациент</th><th>Врач</th><th>Статус</th><th>Счёт</th><th>Следующая запись</th></tr></thead>
          <tbody>{visibleAppointments.map((appointment) => {
            const parts = clinicParts(new Date(appointment.startsAt))
            return <tr key={appointment.appointmentId}>
              <td className="nowrap">{shortDate(parts.date)} · {parts.time}</td>
              <td><Button variant="text" sx={{ p: 0, minWidth: 0, textTransform: 'none' }} onClick={() => onOpenPatient(appointment.patientId)}>{appointment.patientName}</Button></td>
              <td>{appointment.practitionerName}</td>
              <td>{statusLabel(appointment.status)}</td>
              <td>{appointment.amountMinor == null ? '—' : formatMoney(appointment.amountMinor, appointment.currency ?? currency)}<Typography component="span" sx={{ display: 'block', fontSize: 11, color: 'text.secondary' }}>{appointment.chargeStatus ?? 'Не выставлен'}</Typography></td>
              <td>{appointment.withoutNextVisit ? 'Нет' : 'Есть'}</td>
            </tr>
          })}</tbody>
        </table></Box>}
      </Box>
    </Drawer>
  </>
}
