import { useCallback, useEffect, useState } from 'react'
import { HistoryRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { loadAudit } from '../../api/audit'
import type { AuditItem } from '../../api/types'
import { actionLabels, objectLabels, reasonLabel } from './auditLabels'

function formatMoment(iso: string) {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function AuditLog({ apiOnline }: { apiOnline: boolean }) {
  const [items, setItems] = useState<AuditItem[]>([])
  const [result, setResult] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const refresh = useCallback(() => {
    if (!apiOnline) return
    setLoading(true)
    setError('')
    loadAudit(result || undefined)
      .then(setItems)
      .catch((reason: Error) => setError(reason.message))
      .finally(() => setLoading(false))
  }, [apiOnline, result])

  useEffect(() => { refresh() }, [refresh])

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Журнал аудита</Typography><Typography sx={{ color: 'text.secondary' }}>Последние 100 действий сотрудников. Содержание приёмов в журнал не попадает.</Typography></Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <TextField select size="small" label="Результат" value={result} onChange={(event) => setResult(event.target.value)} sx={{ minWidth: 160 }}>
            <MenuItem value="">Все события</MenuItem>
            <MenuItem value="ALLOWED">Разрешено</MenuItem>
            <MenuItem value="DENIED">Отказано</MenuItem>
          </TextField>
          <Button variant="outlined" startIcon={<RefreshRounded />} onClick={refresh} disabled={!apiOnline || loading}>Обновить</Button>
        </Stack>
      </Box>
      {!apiOnline && <Alert severity="info">Журнал аудита хранится на сервере и доступен только при подключённом API.</Alert>}
      {error && <Alert severity="error">{error}</Alert>}
      {apiOnline && !error && (
        <Box className="panel audit-panel">
          {loading && items.length === 0 ? <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress size={28} /></Box> : items.length === 0 ? (
            <Box className="empty" sx={{ minHeight: 160 }}><HistoryRounded /><Typography sx={{ fontWeight: 700 }}>Событий пока нет</Typography></Box>
          ) : (
            <table className="audit-table">
              <thead><tr><th>Время</th><th>Сотрудник</th><th>Действие</th><th>Объект</th><th>Результат</th><th>Основание</th></tr></thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td className="nowrap">{formatMoment(item.occurredAt)}</td>
                    <td>{item.actorName ?? `ID ${item.actorId.slice(-4)}`}</td>
                    <td>{actionLabels[item.action] ?? item.action}</td>
                    <td className="nowrap">{objectLabels[item.objectType] ?? item.objectType} · {item.objectId.slice(-4)}</td>
                    <td><Chip size="small" variant="outlined" label={item.result === 'ALLOWED' ? 'Разрешено' : item.result === 'DENIED' ? 'Отказано' : item.result} color={item.result === 'DENIED' ? 'warning' : 'success'} /></td>
                    <td className="muted-text">{reasonLabel(item.reason)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Box>
      )}
    </>
  )
}
