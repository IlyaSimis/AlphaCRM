import { useEffect, useMemo, useState } from 'react'
import { InsightsRounded } from '@mui/icons-material'
import { Alert, Box, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { loadAppointmentStats, type ApiRole, type Practitioner, type ScheduleItem, type StatRow } from './api'
import { buckets, rowsFromSchedule, shiftIso, summarize, type Bucket, type Counts, type DayStats } from './analytics'

const periods = [
  { days: 7, label: '7 дней' },
  { days: 14, label: '14 дней' },
  { days: 30, label: '30 дней' },
  { days: 90, label: '90 дней' },
]

function shortDate(iso: string) {
  const [, month, day] = iso.split('-')
  return `${day}.${month}`
}

function percent(value: number | null) {
  return value === null ? '—' : `${value}%`
}

function niceMax(value: number) {
  if (value <= 4) return 4
  const step = value <= 10 ? 2 : value <= 30 ? 5 : 10
  return Math.ceil(value / step) * step
}

export function Analytics({ apiOnline, role, anchor, practitioners, scheduleItems }: { apiOnline: boolean; role: ApiRole; anchor: string; practitioners: Practitioner[]; scheduleItems: ScheduleItem[] }) {
  const [periodDays, setPeriodDays] = useState(30)
  const [to, setTo] = useState(anchor)
  const [doctor, setDoctor] = useState('all')
  const [remoteRows, setRemoteRows] = useState<StatRow[] | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [hovered, setHovered] = useState<DayStats | null>(null)
  const from = shiftIso(to, -(periodDays - 1))

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    setLoading(true)
    setError('')
    loadAppointmentStats(from, to, role)
      .then((rows) => { if (!cancelled) setRemoteRows(rows) })
      .catch((reason: Error) => { if (!cancelled) { setRemoteRows(null); setError(reason.message) } })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [apiOnline, from, to, role])

  const rows = apiOnline && remoteRows ? remoteRows : rowsFromSchedule(scheduleItems)
  const stats = useMemo(() => summarize(rows, from, to, doctor === 'all' ? undefined : doctor), [rows, from, to, doctor])
  const maxDay = niceMax(Math.max(0, ...stats.days.map((day) => day.total)))
  const maxDoctor = Math.max(1, ...[...stats.byPractitioner.values()].map((counts) => counts.total))
  const doctorName = (id: string) => practitioners.find((item) => item.id === id)?.fullName ?? 'Врач'
  const labelEvery = Math.ceil(stats.days.length / 10)

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Посещаемость и приёмы</Typography><Typography sx={{ color: 'text.secondary' }}>Только агрегаты: без имён пациентов и клинических данных</Typography></Box>
        <Chip size="small" label={apiOnline ? 'Данные API' : 'Демо-данные из расписания'} color={apiOnline ? 'success' : 'default'} variant="outlined" />
      </Box>
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
      {error && <Alert severity="warning" sx={{ mb: 2 }}>{error}. Показаны данные из расписания.</Alert>}

      <Box className="summary-grid analytics-kpis">
        <Kpi value={String(stats.totals.total)} label="записей за период" note={`${shortDate(from)}–${shortDate(to)}`} />
        <Kpi value={percent(stats.attendanceRate)} label="посещаемость" note={`пришли ${stats.totals.attended} из ${stats.totals.attended + stats.totals.noShow}`} />
        <Kpi value={percent(stats.noShowRate)} label="неявки" note={`${stats.totals.noShow} без предупреждения`} accent={(stats.noShowRate ?? 0) >= 20} />
        <Kpi value={percent(stats.cancelRate)} label="отмены" note={`${stats.totals.cancelled} отменённых записей`} />
      </Box>

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
          <Typography sx={{ fontWeight: 750, mb: 2 }}>Нагрузка по врачам</Typography>
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
    </>
  )
}

function breakdown(counts: Counts) {
  return buckets.map((bucket) => `${bucket.label}: ${counts[bucket.key as Bucket]}`).join(' · ')
}

function Legend() {
  return <Box className="chart-legend">{buckets.map((bucket) => <span key={bucket.key}><i className={`swatch ${bucket.key}`} />{bucket.label}</span>)}</Box>
}

function Kpi({ value, label, note, accent = false }: { value: string; label: string; note: string; accent?: boolean }) {
  return <Box className={`metric ${accent ? 'accent' : ''}`}><Typography className="metric-value">{value}</Typography><Box><Typography sx={{ fontWeight: 700 }}>{label}</Typography><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{note}</Typography></Box></Box>
}
