import { useCallback, useEffect, useState } from 'react'
import { HistoryRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { ACTORS, demoPractitioners, loadAudit, type ApiRole, type AuditItem } from './api'

const actionLabels: Record<string, string> = {
  PATIENT_ADMIN_READ: 'Просмотр карточки пациента',
  PATIENT_CREATED: 'Регистрация пациента',
  PATIENT_UPDATED: 'Изменение данных пациента',
  APPOINTMENT_CREATED: 'Создание записи',
  APPOINTMENT_CANCELLED: 'Отмена записи',
  APPOINTMENT_STATUS_CHANGED: 'Смена статуса записи',
  APPOINTMENT_STATUS_CHANGE: 'Попытка сменить статус',
  ENCOUNTER_STARTED: 'Начало приёма',
  CLINICAL_HISTORY_READ: 'Просмотр клинической истории',
  CLINICAL_NOTE_SAVED: 'Сохранение заметки приёма',
  CLINICAL_ACCESS: 'Доступ к клиническим данным',
  DOCUMENT_DRAFT_CREATED: 'Черновик документа',
  DOCUMENT_SIGNED: 'Подписание документа',
  DOCUMENT_AMENDMENT_CREATED: 'Дополнение к документу',
}

const objectLabels: Record<string, string> = {
  PATIENT: 'Пациент', APPOINTMENT: 'Запись', ENCOUNTER: 'Приём', DOCUMENT: 'Документ', CHARGE: 'Начисление', PAYMENT: 'Платёж',
}

const people: Record<string, string> = Object.fromEntries([
  ...Object.values(ACTORS).map((actor) => [actor.id, actor.name]),
  ...demoPractitioners.map((item) => [item.id, item.fullName]),
])

function formatMoment(iso: string) {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function AuditLog({ apiOnline, role }: { apiOnline: boolean; role: ApiRole }) {
  const [items, setItems] = useState<AuditItem[]>([])
  const [result, setResult] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const refresh = useCallback(() => {
    if (!apiOnline) return
    setLoading(true)
    setError('')
    loadAudit(role, result || undefined)
      .then(setItems)
      .catch((reason: Error) => setError(reason.message))
      .finally(() => setLoading(false))
  }, [apiOnline, role, result])

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
                    <td>{people[item.actorId] ?? `ID ${item.actorId.slice(-4)}`}</td>
                    <td>{actionLabels[item.action] ?? item.action}</td>
                    <td className="nowrap">{objectLabels[item.objectType] ?? item.objectType} · {item.objectId.slice(-4)}</td>
                    <td><Chip size="small" variant="outlined" label={item.result === 'ALLOWED' ? 'Разрешено' : item.result === 'DENIED' ? 'Отказано' : item.result} color={item.result === 'DENIED' ? 'warning' : 'success'} /></td>
                    <td className="muted-text">{item.reason ?? '—'}</td>
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
