import { useEffect, useMemo, useState } from 'react'
import { InsightsRounded } from '@mui/icons-material'
import { Alert, Box, Chip, CircularProgress, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { loadAnalyticsOverview, loadAppointmentStats } from '../../api/analytics'
import type { AnalyticsLoad, AnalyticsOverview, Practitioner, ScheduleItem, StatRow } from '../../api/types'
import { shiftDate, shortDate } from '../../shared/dates'
import { Metric } from '../../shared/ui'
import { formatMoney } from '../schedule/booking'
import { demoOccupancy, demoWeeks, loadRate, waitlistConversion } from './overview'
import { ServiceAnalytics } from './ServiceAnalytics'
import { buckets, rate, rowsFromSchedule, summarize, type Bucket, type Counts, type DayStats } from './stats'

const periods = [
  { days: 28, label: '4 недели' },
  { days: 56, label: '8 недель' },
  { days: 84, label: '12 недель' },
]

function percent(value: number | null) {
  return value === null ? '—' : `${value}%`
}

function niceMax(value: number) {
  if (value <= 4) return 4
  const step = value <= 10 ? 2 : value <= 30 ? 5 : 10
  return Math.ceil(value / step) * step
}

function money(value: number | undefined, currency: string) {
  return value == null ? '—' : formatMoney(value, currency)
}

export function Analytics({ apiOnline, anchor, practitioners, scheduleItems, onOpenPatient }: { apiOnline: boolean; anchor: string; practitioners: Practitioner[]; scheduleItems: ScheduleItem[]; onOpenPatient: (patientId: string) => void }) {
  const [section, setSection] = useState<'overview' | 'services'>('overview')
  const [periodDays, setPeriodDays] = useState(28)
  const [to, setTo] = useState(anchor)
  const [doctor, setDoctor] = useState('all')
  const [remoteRows, setRemoteRows] = useState<StatRow[] | null>(null)
  const [overview, setOverview] = useState<AnalyticsOverview | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [hovered, setHovered] = useState<DayStats | null>(null)
  const from = shiftDate(to, -(periodDays - 1))
  const practitionerId = doctor === 'all' ? undefined : doctor

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    setLoading(true)
    setError('')
    Promise.all([loadAppointmentStats(from, to), loadAnalyticsOverview(from, to, practitionerId)])
      .then(([rows, summary]) => { if (!cancelled) { setRemoteRows(rows); setOverview(summary) } })
      .catch((reason: Error) => { if (!cancelled) { setRemoteRows(null); setOverview(null); setError(reason.message) } })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [apiOnline, from, to, practitionerId])

  const rows = apiOnline && remoteRows ? remoteRows : rowsFromSchedule(scheduleItems)
  const stats = useMemo(() => summarize(rows, from, to, practitionerId), [rows, from, to, practitionerId])
  const demo = useMemo(() => demoOccupancy(scheduleItems, practitioners, from, to, practitionerId), [scheduleItems, practitioners, from, to, practitionerId])
  const weeks = useMemo(() => overview?.weeks ?? (apiOnline ? [] : demoWeeks(scheduleItems, practitioners, from, to, practitionerId)), [overview, apiOnline, scheduleItems, practitioners, from, to, practitionerId])
  const doctorLoads = overview?.practitioners ?? demo.practitioners
  const roomLoads = overview?.rooms ?? demo.rooms
  const totals = overview?.totals
  const currency = overview?.currency ?? 'RUB'
  const totalLoad = totals ? { occupiedMinutes: totals.occupiedMinutes, availableMinutes: totals.availableMinutes } : {
    occupiedMinutes: doctorLoads.reduce((sum, item) => sum + item.occupiedMinutes, 0),
    availableMinutes: doctorLoads.reduce((sum, item) => sum + item.availableMinutes, 0),
  }
  const maxDay = niceMax(Math.max(0, ...stats.days.map((day) => day.total)))
  const maxDoctor = Math.max(1, ...[...stats.byPractitioner.values()].map((counts) => counts.total))
  const doctorName = (id: string) => practitioners.find((item) => item.id === id)?.fullName ?? 'Врач'
  const labelEvery = Math.ceil(stats.days.length / 10)
  const conversion = totals ? waitlistConversion(totals.waitlistBooked, totals.waitlistPeriodTotal) : null

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Показатели клиники</Typography><Typography sx={{ color: 'text.secondary' }}>Общие тренды, посещаемость и выручка по услугам с переходом к исходным записям</Typography></Box>
        <Chip size="small" label={apiOnline ? 'Данные API' : 'Демо-данные из расписания'} color={apiOnline ? 'success' : 'default'} variant="outlined" />
      </Box>
      <ToggleButtonGroup exclusive size="small" value={section} onChange={(_, next: 'overview' | 'services' | null) => { if (next) setSection(next) }} aria-label="Раздел аналитики" sx={{ mb: 2 }}>
        <ToggleButton value="overview">Обзор</ToggleButton>
        <ToggleButton value="services">Услуги</ToggleButton>
      </ToggleButtonGroup>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} className="analytics-filters">
        <TextField select size="small" label="Период" value={periodDays} onChange={(event) => setPeriodDays(Number(event.target.value))}>
          {periods.map((period) => <MenuItem key={period.days} value={period.days}>{period.label}</MenuItem>)}
        </TextField>
        <TextField size="small" type="date" label="По дату" value={to} onChange={(event) => { if (event.target.value) setTo(event.target.value) }} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField select size="small" label="Врач" value={doctor} onChange={(event) => setDoctor(event.target.value)} sx={{ minWidth: 200 }}>
          <MenuItem value="all">Все врачи</MenuItem>
          {practitioners.map((item) => <MenuItem key={item.id} value={item.id}>{item.fullName}</MenuItem>)}
        </TextField>
        {loading && <CircularProgress size={22} sx={{ alignSelf: 'center' }} aria-label="Загрузка аналитики" />}
      </Stack>
      {section === 'overview' && error && <Alert severity="warning" sx={{ mb: 2 }}>{error}. Показаны данные из расписания.</Alert>}
      {section === 'services' ? <ServiceAnalytics apiOnline={apiOnline} from={from} to={to} practitionerId={practitionerId} onOpenPatient={onOpenPatient} /> : <>
      <Box className="summary-grid analytics-kpis">
        <Metric value={String(stats.totals.total)} label="записей за период" note={`${shortDate(from)}–${shortDate(to)}`} />
        <Metric value={percent(stats.attendanceRate)} label="явка" note={`пришли ${stats.totals.attended} из ${stats.totals.attended + stats.totals.noShow}`} />
        <Metric value={percent(stats.noShowRate)} label="no-show" note={`${stats.totals.noShow} без предупреждения`} accent={(stats.noShowRate ?? 0) >= 20} />
        <Metric value={percent(loadRate(totalLoad))} label="загрузка врачей" note={`${totalLoad.occupiedMinutes} из ${totalLoad.availableMinutes} мин`} />
        <Metric value={money(totals?.revenueMinor, currency)} label="выручка" note={totals ? 'оплаты минус возвраты' : 'нужен API'} />
        <Metric value={money(totals?.averageCheckMinor ?? undefined, currency)} label="средний чек" note={totals ? `${totals.payments} оплат` : 'нужен API'} />
        <Metric value={money(totals?.debtMinor, currency)} label="долги по счетам" note={totals ? `${totals.unpaidCharges} открытых счетов` : 'нужен API'} accent={Boolean(totals?.debtMinor)} />
        <Metric value={totals ? `${totals.newPatients} / ${totals.returningPatients}` : '—'} label="новые / повторные" note={totals ? 'пациенты с записями в периоде' : 'нужен API'} />
      </Box>

      <Box className="panel chart-panel" sx={{ mb: 2 }}>
        <Box className="chart-head"><Box><Typography sx={{ fontWeight: 750 }}>Тренды по неделям</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Записи, исходы, загрузка доступного времени и выручка</Typography></Box></Box>
        <table className="audit-table analytics-week-table">
          <thead><tr><th>Неделя с</th><th>Записи</th><th>Явка</th><th>No-show</th><th>Отмены</th><th>Загрузка</th><th>Выручка</th></tr></thead>
          <tbody>{weeks.map((week) => <tr key={week.weekStart}>
            <td className="nowrap">{shortDate(week.weekStart)}</td><td>{week.appointments}</td>
            <td>{percent(rate(week.attended, week.attended + week.noShow))}</td><td>{week.noShow}</td><td>{week.cancelled}</td>
            <td>{percent(loadRate(week))}<Typography component="span" sx={{ display: 'block', fontSize: 12, color: 'text.secondary' }}>{week.occupiedMinutes} / {week.availableMinutes} мин</Typography></td>
            <td className="nowrap">{overview ? formatMoney(week.revenueMinor, currency) : '—'}</td>
          </tr>)}</tbody>
        </table>
      </Box>

      <Box className="analytics-columns" sx={{ mb: 2 }}>
        <LoadPanel title="Загрузка врачей" rows={doctorLoads} />
        <LoadPanel title="Загрузка кабинетов" rows={roomLoads} empty="Кабинеты не указаны у врачей" />
      </Box>

      {overview && <>
        <Box className="analytics-columns" sx={{ mb: 2 }}>
          <Box className="panel chart-panel">
            <Typography sx={{ fontWeight: 750, mb: 2 }}>Воронка новых пациентов</Typography>
            <FunnelRow label="Первый контакт" value={overview.funnel.contacts} total={overview.funnel.contacts} />
            <FunnelRow label="Запись" value={overview.funnel.booked} total={overview.funnel.contacts} />
            <FunnelRow label="Завершённый визит" value={overview.funnel.visited} total={overview.funnel.contacts} />
            <FunnelRow label="Оплата" value={overview.funnel.paid} total={overview.funnel.contacts} />
            <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 1.5 }}>Когорта карточек, созданных в выбранном периоде</Typography>
          </Box>
          <Box className="panel chart-panel">
            <Typography sx={{ fontWeight: 750, mb: 2 }}>Запись и удержание</Typography>
            <Box className="status-total"><Typography sx={{ flex: 1, fontSize: 14 }}>Лист ожидания сейчас</Typography><Typography sx={{ fontWeight: 750 }}>{totals?.waitlistActive}</Typography></Box>
            <Box className="status-total"><Typography sx={{ flex: 1, fontSize: 14 }}>Конверсия в запись за период</Typography><Typography sx={{ fontWeight: 750 }}>{percent(conversion)}</Typography><Typography sx={{ width: 80, textAlign: 'right', fontSize: 13, color: 'text.secondary' }}>{totals?.waitlistBooked} из {totals?.waitlistPeriodTotal}</Typography></Box>
            <Box className="status-total"><Typography sx={{ flex: 1, fontSize: 14 }}>Без следующего визита</Typography><Typography sx={{ fontWeight: 750 }}>{percent(rate(totals?.withoutNextVisit ?? 0, totals?.completedPatients ?? 0))}</Typography><Typography sx={{ width: 80, textAlign: 'right', fontSize: 13, color: 'text.secondary' }}>{totals?.withoutNextVisit} из {totals?.completedPatients}</Typography></Box>
            <Box className="status-total"><Typography sx={{ flex: 1, fontSize: 14 }}>Свободный прайс в оплатах</Typography><Typography sx={{ fontWeight: 750 }}>{percent(rate(totals?.openPriceMinor ?? 0, totals ? totals.revenueMinor + totals.refundedMinor : 0))}</Typography></Box>
          </Box>
        </Box>

        <Box className="analytics-columns" sx={{ mb: 2 }}>
          <Box className="panel chart-panel">
            <Typography sx={{ fontWeight: 750, mb: 1 }}>Ближайшее свободное время по специальности</Typography>
            <Typography sx={{ fontSize: 12, color: 'text.secondary', mb: 2 }}>Для слота 30 минут, поиск на 30 дней вперёд</Typography>
            {overview.nextSlots.length === 0 ? <Typography color="text.secondary">Свободных слотов по действующему графику не найдено</Typography> : overview.nextSlots.map((slot) => <Box key={slot.specialty} className="status-total">
              <Typography sx={{ flex: 1, fontSize: 14 }}>{slot.specialty}<Typography component="span" sx={{ display: 'block', fontSize: 12, color: 'text.secondary' }}>ближайший: {new Date(slot.nextAvailableAt).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</Typography></Typography>
              <Typography sx={{ fontWeight: 750 }}>в среднем {String(slot.averageHours).replace('.', ',')} ч</Typography>
            </Box>)}
          </Box>
          <Box className="panel chart-panel">
            <Typography sx={{ fontWeight: 750, mb: 2 }}>Операционные очереди</Typography>
            <Box className="summary-grid" sx={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
              <Metric value={String(overview.queues.unclosedEncounters)} label="незакрытые приёмы" note="требуют завершения" accent={overview.queues.unclosedEncounters > 0} />
              <Metric value={String(overview.queues.unsignedDocuments)} label="неподписанные" note="документы и PDF" accent={overview.queues.unsignedDocuments > 0} />
              <Metric value={String(overview.queues.unpaidCharges)} label="счета без оплаты" note="открытые счета" accent={overview.queues.unpaidCharges > 0} />
            </Box>
          </Box>
        </Box>
      </>}

      <Box className="panel chart-panel">
        <Box className="chart-head">
          <Box><Typography sx={{ fontWeight: 750 }}>Приёмы по дням</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Количество записей и их итог</Typography></Box>
          <Legend />
        </Box>
        <Box className="chart-tooltip" aria-live="polite">
          {hovered ? <><b>{shortDate(hovered.date)}</b> · всего {hovered.total}{buckets.map((bucket) => hovered[bucket.key] ? <span key={bucket.key}><i className={`swatch ${bucket.key}`} />{bucket.label}: {hovered[bucket.key]}</span> : null)}</> : <span className="muted-text">Наведите на столбец, чтобы увидеть детали дня</span>}
        </Box>
        {stats.totals.total === 0 ? <Box className="empty" sx={{ minHeight: 200 }}><InsightsRounded /><Typography sx={{ fontWeight: 700 }}>За этот период записей нет</Typography></Box> : (
          <Box className="day-chart" role="img" aria-label={`Столбчатая диаграмма приёмов по дням, максимум ${maxDay} в день`}>
            <Box className="day-chart-grid">
              {[maxDay, maxDay / 2, 0].map((tick) => <Box key={tick} className="grid-line"><span>{Number.isInteger(tick) ? tick : tick.toFixed(1)}</span></Box>)}
            </Box>
            <Box className="day-chart-bars">
              {stats.days.map((day, index) => (
                <Box key={day.date} className={`day-column ${hovered?.date === day.date ? 'hovered' : ''}`} onMouseEnter={() => setHovered(day)} onMouseLeave={() => setHovered(null)} onFocus={() => setHovered(day)} onBlur={() => setHovered(null)} tabIndex={0} aria-label={`${shortDate(day.date)}: всего ${day.total}`}>
                  <Box className="day-stack" sx={{ height: `${(day.total / maxDay) * 100}%` }}>
                    {buckets.map((bucket) => day[bucket.key] > 0 && <Box key={bucket.key} className={`segment ${bucket.key}`} sx={{ flexGrow: day[bucket.key] }} />)}
                  </Box>
                  <span className="day-label">{index % labelEvery === 0 ? shortDate(day.date) : ''}</span>
                </Box>
              ))}
            </Box>
          </Box>
        )}
      </Box>

      <Box className="analytics-columns">
        <Box className="panel chart-panel">
          <Typography sx={{ fontWeight: 750, mb: 2 }}>Итоги по врачам</Typography>
          {stats.byPractitioner.size === 0 && <Typography sx={{ color: 'text.secondary' }}>Нет данных</Typography>}
          {[...stats.byPractitioner.entries()].sort((a, b) => b[1].total - a[1].total).map(([id, counts]) => (
            <Box key={id} className="doctor-bar">
              <Box className="doctor-bar-label"><Typography sx={{ fontWeight: 650, fontSize: 14 }}>{doctorName(id)}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{counts.total} · посещаемость {percent(counts.attended + counts.noShow ? Math.round((counts.attended / (counts.attended + counts.noShow)) * 100) : null)}</Typography></Box>
              <Box className="doctor-bar-track" title={breakdown(counts)}>
                <Box className="doctor-bar-fill" sx={{ width: `${(counts.total / maxDoctor) * 100}%` }}>
                  {buckets.map((bucket) => counts[bucket.key] > 0 && <Box key={bucket.key} className={`segment ${bucket.key}`} sx={{ flexGrow: counts[bucket.key] }} />)}
                </Box>
              </Box>
            </Box>
          ))}
        </Box>
        <Box className="panel chart-panel">
          <Typography sx={{ fontWeight: 750, mb: 2 }}>Итоги записей</Typography>
          {buckets.map((bucket) => (
            <Box key={bucket.key} className="status-total">
              <i className={`swatch ${bucket.key}`} />
              <Typography sx={{ flex: 1, fontSize: 14 }}>{bucket.label}</Typography>
              <Typography sx={{ fontWeight: 750 }}>{stats.totals[bucket.key]}</Typography>
              <Typography sx={{ width: 48, textAlign: 'right', fontSize: 13, color: 'text.secondary' }}>{percent(stats.totals.total ? Math.round((stats.totals[bucket.key] / stats.totals.total) * 100) : null)}</Typography>
            </Box>
          ))}
        </Box>
      </Box>

      <details className="panel table-view">
        <summary>Таблица по дням</summary>
        <table>
          <thead><tr><th>Дата</th>{buckets.map((bucket) => <th key={bucket.key}>{bucket.label}</th>)}<th>Всего</th></tr></thead>
          <tbody>{stats.days.filter((day) => day.total > 0).map((day) => <tr key={day.date}><td>{shortDate(day.date)}</td>{buckets.map((bucket) => <td key={bucket.key}>{day[bucket.key]}</td>)}<td>{day.total}</td></tr>)}</tbody>
        </table>
      </details>
      </>}
    </>
  )
}

function LoadPanel({ title, rows, empty = 'Нет данных о сменах' }: { title: string; rows: AnalyticsLoad[]; empty?: string }) {
  return <Box className="panel chart-panel">
    <Typography sx={{ fontWeight: 750, mb: 2 }}>{title}</Typography>
    {rows.length === 0 ? <Typography color="text.secondary">{empty}</Typography> : rows.map((row) => {
      const value = loadRate(row)
      return <Box key={row.id} className="doctor-bar">
        <Box className="doctor-bar-label"><Typography sx={{ fontWeight: 650, fontSize: 14 }}>{row.label}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{percent(value)} · {row.occupiedMinutes} из {row.availableMinutes} мин</Typography></Box>
        <Box className="doctor-bar-track"><Box className="doctor-bar-fill" sx={{ width: `${value ?? 0}%` }} /></Box>
      </Box>
    })}
  </Box>
}

function FunnelRow({ label, value, total }: { label: string; value: number; total: number }) {
  return <Box className="status-total"><Typography sx={{ flex: 1, fontSize: 14 }}>{label}</Typography><Typography sx={{ fontWeight: 750 }}>{value}</Typography><Typography sx={{ width: 48, textAlign: 'right', fontSize: 13, color: 'text.secondary' }}>{percent(rate(value, total))}</Typography></Box>
}

function breakdown(counts: Counts) {
  return buckets.map((bucket) => `${bucket.label}: ${counts[bucket.key as Bucket]}`).join(' · ')
}

function Legend() {
  return <Box className="chart-legend">{buckets.map((bucket) => <span key={bucket.key}><i className={`swatch ${bucket.key}`} />{bucket.label}</span>)}</Box>
}
