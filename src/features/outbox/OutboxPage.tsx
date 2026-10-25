import { useCallback, useEffect, useState } from 'react'
import { OutboxRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Chip, CircularProgress, Typography } from '@mui/material'
import { loadOutbox } from '../../api/outbox'
import type { OutboxItem } from '../../api/types'
import { channelLabels, reasonText, statusLabels, statusTone, templateLabels } from './outboxLabels'

/** Время записи — как в расписании (UTC); время событий — как в журнале аудита. */
function appointmentTime(iso: string | null) {
  if (!iso) return '—'
  return `${iso.slice(8, 10)}.${iso.slice(5, 7)}.${iso.slice(0, 4)} ${iso.slice(11, 16)}`
}

function moment(iso: string | null) {
  if (!iso) return '—'
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

/** Последние внешние выводы клиники: сообщения пациентам и чеки. Только администратору. */
export function OutboxPage({ apiOnline }: { apiOnline: boolean }) {
  const [items, setItems] = useState<OutboxItem[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const refresh = useCallback(() => {
    if (!apiOnline) return
    setLoading(true)
    setError('')
    loadOutbox()
      .then(setItems)
      .catch((reason: Error) => setError(reason.message))
      .finally(() => setLoading(false))
  }, [apiOnline])

  useEffect(() => { refresh() }, [refresh])

  return (
    <>
      <Box className="section-intro">
        <Box>
          <Typography variant="h4" sx={{ fontWeight: 750 }}>Внешние выводы</Typography>
          <Typography sx={{ color: 'text.secondary' }}>Последние 100 сообщений пациентам и чеков. Текст сообщения и контакт пациента здесь не показываются.</Typography>
        </Box>
        <Button variant="outlined" startIcon={<RefreshRounded />} onClick={refresh} disabled={!apiOnline || loading}>Обновить</Button>
      </Box>
      {!apiOnline && <Alert severity="info">Внешние выводы хранятся на сервере и доступны только при подключённом API. Демо-песочница ничего наружу не отправляет.</Alert>}
      {error && <Alert severity="error">{error}</Alert>}
      {apiOnline && !error && (
        <Box className="panel audit-panel">
          {loading && items.length === 0 ? <Box className="empty" sx={{ minHeight: 160 }}><CircularProgress size={28} /></Box> : items.length === 0 ? (
            <Box className="empty" sx={{ minHeight: 160 }}><OutboxRounded /><Typography sx={{ fontWeight: 700 }}>Выводов пока нет</Typography></Box>
          ) : (
            <table className="audit-table">
              <thead><tr><th>Создано</th><th>Шаблон</th><th>Запись</th><th>Канал</th><th>Статус</th><th>Последняя попытка</th></tr></thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td className="nowrap">{moment(item.createdAt)}</td>
                    <td>{templateLabels[item.template] ?? item.template}</td>
                    <td className="nowrap">{item.aggregateType === 'PAYMENT' ? `Платёж · ${item.aggregateId.slice(-4)}` : `${appointmentTime(item.appointmentStartsAt)} · ${item.aggregateId.slice(-4)}`}</td>
                    <td>{item.channel ? channelLabels[item.channel] ?? item.channel : '—'}</td>
                    <td>
                      <Chip size="small" variant="outlined" label={statusLabels[item.status] ?? item.status} color={statusTone[item.status] ?? 'default'} />
                      {item.reason && <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 0.5 }}>{reasonText(item.reason)}</Typography>}
                    </td>
                    <td className="nowrap">{item.status === 'SENT' ? `доставлено ${moment(item.sentAt)}` : moment(item.lastAttemptAt)}{item.nextAttemptAt && item.status !== 'SENT' ? <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>следующая {moment(item.nextAttemptAt)}</Typography> : null}</td>
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
