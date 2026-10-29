import { useEffect, useState } from 'react'
import { Alert, Box, Typography } from '@mui/material'
import { loadDashboard } from '../../api/insights'
import type { Dashboard, DashboardWidget } from '../../api/types'
import { Metric } from '../../shared/ui'
import { formatMoney } from '../schedule/booking'
import { formatBytes, widgetText } from './dashboard'

type DashboardWidgetsProps = {
  /** Только живой сервер: песочница дашборд не считает. */
  apiOnline: boolean
  /** Если задано, показываются только нужные этой странице показатели. */
  codes?: string[]
}

/**
 * Персональный дашборд (сценарий D9): виджеты по роли сверху страницы «Сегодня» (у врача — над
 * расписанием). Только числа: ни ФИО пациентов, ни клинического текста.
 */
export function DashboardWidgets({ apiOnline, codes }: DashboardWidgetsProps) {
  const [dashboard, setDashboard] = useState<Dashboard | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    loadDashboard()
      .then((value) => { if (!cancelled) setDashboard(value) })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline])

  if (!apiOnline) return null
  if (error) return <Alert severity="warning" sx={{ mb: 2 }}>{error}</Alert>
  const widgets = (dashboard?.widgets ?? []).filter((widget) => widgetText(widget) !== null && (!codes || codes.includes(widget.code)))
  if (widgets.length === 0) return null
  return (
    <Box component="section" aria-label="Мой дашборд" className="dashboard-grid" sx={{ mb: 2 }}>
      {widgets.map((widget) => <Widget key={widget.code} widget={widget} />)}
    </Box>
  )
}

function Widget({ widget }: { widget: DashboardWidget }) {
  const text = widgetText(widget)
  if (!text) return null
  const value = widget.unit === 'money' ? formatMoney(widget.value) : widget.unit === 'bytes' ? formatBytes(widget.value) : String(widget.value)
  const note = widget.code === 'salary.month' ? salaryNote(widget.items[0]) : text.note
  return (
    <Box>
      <Metric value={value} label={text.label} note={note} accent={text.accent} />
      {widget.code !== 'salary.month' && widget.items.length > 0 && <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 0.5, ml: 1 }}>Ближайшие: {widget.items.join(', ')}</Typography>}
    </Box>
  )
}

function salaryNote(unit?: string) {
  switch (unit) {
    case 'MONTH': return 'оклад по действующим условиям'
    case 'HOUR': return 'по месячному графику'
    case 'VISIT': return 'за завершённые приёмы'
    default: return 'условия работы не внесены'
  }
}
