import { useCallback, useEffect, useState } from 'react'
import { HistoryRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, MenuItem, Pagination, Stack, TextField, Typography } from '@mui/material'
import { AUDIT_PAGE_SIZE, loadAuditPage } from '../../api/audit'
import type { AuditItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { actionLabels, objectLabels, reasonLabel } from './auditLabels'

const formatMoment = (iso: string) => formatClinicMoment(iso, true)

export function AuditLog({ apiOnline }: { apiOnline: boolean }) {
  const [items, setItems] = useState<AuditItem[]>([])
  const [total, setTotal] = useState(0)
  // Номер страницы на экране — с единицы; сервер считает с нуля.
  const [page, setPage] = useState(1)
  const [result, setResult] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const refresh = useCallback(() => {
    if (!apiOnline) return
    setLoading(true)
    setError('')
    loadAuditPage(page - 1, result || undefined)
      .then((rows) => { setItems(rows.items); setTotal(rows.total) })
      .catch((reason: Error) => setError(reason.message))
      .finally(() => setLoading(false))
  }, [apiOnline, result, page])
  const pages = Math.max(1, Math.ceil(total / AUDIT_PAGE_SIZE))

  useEffect(() => { refresh() }, [refresh])

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Журнал аудита</Typography><Typography sx={{ color: 'text.secondary' }}>Действия сотрудников, новые сверху, по 20 на странице. Содержание приёмов в журнал не попадает.</Typography></Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <TextField select size="small" label="Результат" value={result} onChange={(event) => { setResult(event.target.value); setPage(1) }} sx={{ minWidth: 160 }}>
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
          {pages > 1 && <Box sx={{ display: 'flex', justifyContent: 'center', mt: 2 }}>
            <Pagination count={pages} page={page} onChange={(_, next) => setPage(next)} color="primary" shape="rounded" getItemAriaLabel={(type, number) => type === 'page' ? `Страница ${number}` : type === 'next' ? 'Следующая страница' : type === 'previous' ? 'Предыдущая страница' : type === 'first' ? 'Первая страница' : 'Последняя страница'} />
          </Box>}
        </Box>
      )}
    </>
  )
}
