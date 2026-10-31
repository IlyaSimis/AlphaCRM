import { useEffect, useState } from 'react'
import { Alert, Box, ButtonBase, Typography } from '@mui/material'
import { loadDashboard } from '../../api/insights'
import type { Dashboard, DashboardWidget } from '../../api/types'
import { canOpen, type Page } from '../../app/pages'
import { Metric } from '../../shared/ui'
import type { Role } from '../../shared/roles'
import { formatMoney } from '../schedule/booking'
import { dashboardDestination, formatBytes, widgetText } from './dashboard'

type DashboardWidgetsProps = {
  /** Только живой сервер: песочница дашборд не считает. */
  apiOnline: boolean
  role: Role
  /** Если задано, показываются только нужные этой странице показатели в порядке кодов. */
  codes?: string[]
  /** Одна колонка для размещения под личными заметками. */
  stacked?: boolean
  onOpen?: (page: Page) => void
}

/**
 * Персональный дашборд (сценарий D9): виджеты по роли сверху страницы «Сегодня» (у врача — над
 * расписанием). Только числа: ни ФИО пациентов, ни клинического текста.
 */
export function DashboardWidgets({ apiOnline, role, codes, stacked = false, onOpen }: DashboardWidgetsProps) {
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
  const available = (dashboard?.widgets ?? []).filter((widget) => widgetText(widget) !== null)
  const widgets = codes
    ? codes.flatMap((code) => available.filter((widget) => widget.code === code))
    : available
  if (widgets.length === 0) return null
  return (
    <Box component="section" aria-label="Мой дашборд" className="dashboard-grid" sx={{ mb: stacked ? 0 : 2, gridTemplateColumns: stacked ? '1fr' : undefined }}>
      {widgets.map((widget) => <Widget key={widget.code} widget={widget} role={role} onOpen={onOpen} />)}
    </Box>
  )
}

function Widget({ widget, role, onOpen }: { widget: DashboardWidget; role: Role; onOpen?: (page: Page) => void }) {
  const text = widgetText(widget)
  if (!text) return null
  const value = widget.unit === 'money' ? formatMoney(widget.value) : widget.unit === 'bytes' ? formatBytes(widget.value) : String(widget.value)
  const note = widget.code === 'salary.month' ? salaryNote(widget.items[0]) : text.note
  const destination = dashboardDestination(widget.code)
  const content = <>
    <Metric value={value} label={text.label} note={note} accent={text.accent} />
    {widget.code !== 'salary.month' && widget.items.length > 0 && <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 0.5, ml: 1 }}>Ближайшие: {widget.items.join(', ')}</Typography>}
  </>
  if (!destination || !onOpen || !canOpen(destination, role)) return <Box>{content}</Box>
  return (
    <ButtonBase
      component="button"
      type="button"
      aria-label={`Открыть раздел: ${text.label}`}
      onClick={() => onOpen(destination)}
      sx={{
        width: '100%', p: 0, borderRadius: '14px', textAlign: 'left', display: 'flex', flexDirection: 'column', alignItems: 'stretch',
        '& .metric': { width: '100%', transition: 'border-color 120ms ease, box-shadow 120ms ease' },
        '&:hover .metric, &.Mui-focusVisible .metric': { borderColor: 'primary.main', boxShadow: 1 },
      }}
    >
      {content}
    </ButtonBase>
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
