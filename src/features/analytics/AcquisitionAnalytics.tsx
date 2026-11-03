import { useCallback, useEffect, useState } from 'react'
import { Alert, Box, Button, CircularProgress, Typography } from '@mui/material'
import { loadAcquisition } from '../../api/analytics'
import type { AcquisitionAnalytics as AcquisitionAnalyticsData } from '../../api/types'
import { shortDate } from '../../shared/dates'
import { Metric } from '../../shared/ui'
import { referralLabel } from '../registry/registry'
import { formatMoney } from '../schedule/booking'
import { rate } from './stats'

const integer = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 0 })
const decimal = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 })

function percent(value: number | null) {
  return value === null ? '—' : `${decimal.format(value)}%`
}

function duration(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const remainder = Math.round(seconds % 60)
  return minutes > 0 ? `${minutes} мин ${remainder} с` : `${remainder} с`
}

function sourceName(source: string) {
  return source === 'UNSPECIFIED' ? 'Не указан' : referralLabel(source)
}

export function AcquisitionAnalytics({ apiOnline, from, to }: { apiOnline: boolean; from: string; to: string }) {
  const [data, setData] = useState<AcquisitionAnalyticsData | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setData(await loadAcquisition(from, to))
    } catch (reason) {
      setData(null)
      setError((reason as Error).message)
    } finally {
      setLoading(false)
    }
  }, [from, to])

  useEffect(() => {
    if (apiOnline) void reload()
  }, [apiOnline, reload])

  if (!apiOnline) return <Alert severity="info">Аналитика привлечения доступна только при подключённом API.</Alert>
  if (loading && !data) return <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}><CircularProgress aria-label="Загрузка аналитики привлечения" /></Box>
  if (error) return <Alert severity="error" action={<Button onClick={() => void reload()}>Повторить</Button>}>{error}</Alert>
  if (!data) return null

  const traffic = data.web.traffic
  return <Box sx={{ display: 'grid', gap: 2 }}>
    <Box className="panel chart-panel">
      <Typography variant="h6" sx={{ fontWeight: 750 }}>Источники пациентов в CRM</Typography>
      <Typography sx={{ color: 'text.secondary', fontSize: 13, mb: 2 }}>Когорта карточек, созданных за выбранный период. Конверсии считаются от всех карточек источника; выручка накопленная по этой когорте, оплаты минус возвраты.</Typography>
      {data.referralSources.length === 0 ? <Typography color="text.secondary">За выбранный период новых карточек пациентов нет.</Typography> : <Box sx={{ overflowX: 'auto' }}>
        <table className="audit-table analytics-week-table">
          <thead><tr><th>Источник</th><th>Карточки</th><th>Запись</th><th>Завершённый визит</th><th>Оплата</th><th>Выручка когорты</th></tr></thead>
          <tbody>{data.referralSources.map((row) => <tr key={row.source}>
            <td>{sourceName(row.source)}</td>
            <td>{integer.format(row.contacts)}</td>
            <td>{integer.format(row.booked)}<Typography component="span" sx={{ display: 'block', fontSize: 12, color: 'text.secondary' }}>{percent(rate(row.booked, row.contacts))}</Typography></td>
            <td>{integer.format(row.visited)}<Typography component="span" sx={{ display: 'block', fontSize: 12, color: 'text.secondary' }}>{percent(rate(row.visited, row.contacts))}</Typography></td>
            <td>{integer.format(row.paid)}<Typography component="span" sx={{ display: 'block', fontSize: 12, color: 'text.secondary' }}>{percent(rate(row.paid, row.contacts))}</Typography></td>
            <td className="nowrap">{formatMoney(row.revenueMinor, data.currency)}</td>
          </tr>)}</tbody>
        </table>
      </Box>}
    </Box>

    <Box className="panel chart-panel">
      <Typography variant="h6" sx={{ fontWeight: 750 }}>Сайт и Яндекс Метрика</Typography>
      <Typography sx={{ color: 'text.secondary', fontSize: 13, mb: 2 }}>Веб-показатели за тот же период. Идентификаторы посетителей и cookies в CRM не загружаются.</Typography>
      {data.web.state === 'NOT_CONFIGURED' && <Alert severity="info">Метрика не подключена. Укажите ID счётчика и OAuth-токен в разделе «Управление → Интеграции», затем активируйте интеграцию.</Alert>}
      {data.web.state === 'DISABLED' && <Alert severity="info">Интеграция с Метрикой отключена. Активируйте её в разделе «Управление → Интеграции».</Alert>}
      {data.web.state === 'ERROR' && <Alert severity="warning">Не удалось получить данные Метрики{data.web.detail ? `: ${data.web.detail}` : '.'} Показатели CRM выше остаются доступны.</Alert>}
      {data.web.state === 'READY' && traffic && <>
        <Box className="summary-grid analytics-kpis" sx={{ mb: 2 }}>
          <Metric value={integer.format(traffic.visits)} label="визиты" note="сессии на сайте" />
          <Metric value={integer.format(traffic.users)} label="посетители" note={`${percent(traffic.newVisitorsPercent)} новых`} />
          <Metric value={integer.format(traffic.pageviews)} label="просмотры" note={`глубина ${decimal.format(traffic.pageDepth)}`} />
          <Metric value={percent(traffic.bounceRate)} label="отказы" note="доля визитов по Метрике" />
          <Metric value={duration(traffic.averageVisitDurationSeconds)} label="средний визит" note="длительность сессии" />
          <Metric value={integer.format(traffic.goalReaches)} label="достижения целей" note="все цели счётчика" />
          <Metric value={percent(traffic.goalConversionRate)} label="конверсия в цель" note="визиты с любой целью" />
        </Box>
        <Box className="analytics-columns">
          <Box>
            <Typography sx={{ fontWeight: 750, mb: 1 }}>Трафик по дням</Typography>
            {data.web.daily.length === 0 ? <Typography color="text.secondary">Нет данных за выбранный период.</Typography> : <Box sx={{ overflowX: 'auto' }}>
              <table className="audit-table analytics-week-table">
                <thead><tr><th>Дата</th><th>Визиты</th><th>Посетители</th><th>Цели</th></tr></thead>
                <tbody>{data.web.daily.map((row) => <tr key={row.date}><td className="nowrap">{shortDate(row.date)}</td><td>{integer.format(row.visits)}</td><td>{integer.format(row.users)}</td><td>{integer.format(row.goalReaches)}</td></tr>)}</tbody>
              </table>
            </Box>}
          </Box>
          <Box>
            <Typography sx={{ fontWeight: 750, mb: 1 }}>Источники трафика</Typography>
            {data.web.sources.length === 0 ? <Typography color="text.secondary">Нет данных за выбранный период.</Typography> : <Box sx={{ overflowX: 'auto' }}>
              <table className="audit-table analytics-week-table">
                <thead><tr><th>Источник</th><th>Визиты</th><th>Посетители</th><th>Цели</th></tr></thead>
                <tbody>{data.web.sources.map((row) => <tr key={row.source}><td>{row.source}</td><td>{integer.format(row.visits)}</td><td>{integer.format(row.users)}</td><td>{integer.format(row.goalReaches)}</td></tr>)}</tbody>
              </table>
            </Box>}
          </Box>
        </Box>
      </>}
    </Box>
  </Box>
}
