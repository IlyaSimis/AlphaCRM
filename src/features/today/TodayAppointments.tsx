import { useEffect, useState } from 'react'
import { Alert, Box, Button, Chip, CircularProgress, Typography } from '@mui/material'
import { loadSchedule } from '../../api/schedule'
import type { Practitioner, ScheduleItem } from '../../api/types'
import { localToday } from '../../shared/dates'

type TodayAppointmentsProps = {
  apiOnline: boolean
  appointments: ScheduleItem[]
  practitioners: Practitioner[]
  revision: number
  onOpen: () => void
}

/** Все записи текущего дня, независимо от даты, выбранной в основном расписании. */
export function TodayAppointments({ apiOnline, appointments, practitioners, revision, onOpen }: TodayAppointmentsProps) {
  const today = localToday()
  const [items, setItems] = useState<ScheduleItem[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    loadSchedule(today)
      .then((rows) => { if (!cancelled) { setItems(rows); setError('') } })
      .catch(() => { if (!cancelled) setError('Не удалось загрузить приёмы на сегодня') })
    return () => { cancelled = true }
  }, [apiOnline, today, revision])

  const loaded = apiOnline ? items : appointments
  const visible = (loaded ?? [])
    .filter((item) => item.date === today && item.statusCode !== 'CANCELLED')
    .sort((left, right) => left.time.localeCompare(right.time))
  const doctorName = (id: string) => practitioners.find((practitioner) => practitioner.id === id)?.fullName ?? '—'

  return (
    <Box className="panel" component="section" aria-label="Приёмы сегодня">
      <Box className="panel-title-row">
        <Typography className="panel-label">ПРИЁМЫ СЕГОДНЯ</Typography>
        <Button size="small" onClick={onOpen}>Расписание</Button>
      </Box>
      {error && <Alert severity="warning" sx={{ mt: 1 }}>{error}</Alert>}
      {!error && loaded === null && <Box className="empty" sx={{ minHeight: 100 }}><CircularProgress size={24} aria-label="Загрузка приёмов на сегодня" /></Box>}
      {!error && loaded !== null && visible.length === 0 && <Typography sx={{ color: 'text.secondary', mt: 1 }}>На сегодня приёмов нет.</Typography>}
      {visible.length > 0 && <Box sx={{ overflowX: 'auto', mt: 1 }}>
        <table className="audit-table today-appointments-table">
          <thead><tr><th>Время</th><th>Пациент</th><th>Услуга</th><th>Врач</th><th>Статус</th></tr></thead>
          <tbody>{visible.map((item) => <tr key={item.id}>
            <td className="nowrap" style={{ fontWeight: 700 }}>{item.time}</td>
            <td>{item.name}</td>
            <td>{item.type}</td>
            <td>{doctorName(item.practitionerId)}</td>
            <td><Chip size="small" variant="outlined" label={item.status} /></td>
          </tr>)}</tbody>
        </table>
      </Box>}
    </Box>
  )
}
