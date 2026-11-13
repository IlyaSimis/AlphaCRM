import { useEffect, useMemo, useState } from 'react'
import { Alert, Box, Button, Chip, CircularProgress, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { loadRegistryWorkQueue } from '../../api/registry'
import { changeAppointmentStatus } from '../../api/schedule'
import type { Practitioner, ScheduleItem, WorkQueueItem, WorkQueueKind } from '../../api/types'
import { clinicParts } from '../../shared/clinicTime'
import { localToday } from '../../shared/dates'
import { readQueueFilter, writeQueueFilter, type QueueFilter } from './workQueueFilters'

const kindLabels: Record<WorkQueueKind, string> = {
  CONFIRMATION: 'Подтверждения',
  PAYMENT: 'Оплаты',
  REBOOK: 'Повторная запись',
  NO_SHOW: 'Неявки',
  DELIVERY: 'Ошибки доставки',
}

const kindOrder: WorkQueueKind[] = ['CONFIRMATION', 'PAYMENT', 'REBOOK', 'NO_SHOW', 'DELIVERY']

type AdminWorkQueueProps = {
  selfId: string
  apiOnline: boolean
  appointments: ScheduleItem[]
  practitioners: Practitioner[]
  revision: number
  onOpenPatient: (patientId: string) => void
  onOpenCashier: () => void
  onRebook: (item: WorkQueueItem) => void
  onChanged: () => void
}

/** Демо-песочница показывает только те причины, которые можно вывести из записей без счетов. */
export function demoWorkQueue(appointments: ScheduleItem[], practitioners: Practitioner[], today: string): WorkQueueItem[] {
  const doctorName = (id: string) => practitioners.find((item) => item.id === id)?.fullName ?? 'Врач'
  return appointments
    .filter((item) => item.date === today)
    .flatMap((item): WorkQueueItem[] => {
      const base = {
        appointmentId: item.id ?? item.patientId,
        patientId: item.patientId,
        patientName: item.name,
        practitionerId: item.practitionerId,
        practitionerName: doctorName(item.practitionerId),
        startsAt: `${item.date}T${item.time}:00Z`,
        serviceName: item.type,
        appointmentStatus: item.statusCode,
        chargeId: null,
        amountMinor: null,
        currency: null,
      }
      if (item.statusCode === 'BOOKED') return [{ ...base, id: `confirmation:${base.appointmentId}`, kind: 'CONFIRMATION', reason: 'Запись ещё не подтверждена' }]
      if (item.statusCode === 'COMPLETED') return [{ ...base, id: `rebook:${base.appointmentId}`, kind: 'REBOOK', reason: 'Проверьте, нужна ли следующая запись' }]
      if (item.statusCode === 'NO_SHOW') return [{ ...base, id: `no_show:${base.appointmentId}`, kind: 'NO_SHOW', reason: 'Нужно связаться с пациентом' }]
      return []
    })
}

export function AdminWorkQueue({ selfId, apiOnline, appointments, practitioners, revision, onOpenPatient, onOpenCashier, onRebook, onChanged }: AdminWorkQueueProps) {
  const today = localToday()
  const [remote, setRemote] = useState<WorkQueueItem[] | null>(null)
  const [error, setError] = useState('')
  const [filter, setFilter] = useState<QueueFilter>(() => readQueueFilter(selfId))
  const [busy, setBusy] = useState('')
  const [localRevision, setLocalRevision] = useState(0)
  const [dismissed, setDismissed] = useState<string[]>([])
  const demoItems = useMemo(() => demoWorkQueue(appointments, practitioners, today), [appointments, practitioners, today])

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    loadRegistryWorkQueue(today)
      .then((result) => { if (!cancelled) { setRemote(result.items); setError('') } })
      .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    return () => { cancelled = true }
  }, [apiOnline, today, revision, localRevision])

  const loaded = apiOnline ? remote : demoItems
  const items = (loaded ?? []).filter((item) => !dismissed.includes(item.id))
  const visible = filter === 'ALL' ? items : items.filter((item) => item.kind === filter)
  const selectFilter = (next: QueueFilter) => {
    setFilter(next)
    writeQueueFilter(selfId, next)
  }

  const confirm = async (item: WorkQueueItem) => {
    setBusy(item.id)
    setError('')
    try {
      if (apiOnline) await changeAppointmentStatus(item.appointmentId, 'CONFIRMED')
      setDismissed((current) => [...current, item.id])
      setLocalRevision((value) => value + 1)
      onChanged()
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy('')
    }
  }

  const action = (item: WorkQueueItem) => {
    if (item.kind === 'CONFIRMATION') return <Button size="small" variant="contained" disabled={busy === item.id} aria-label={`Подтвердить запись ${item.patientName}`} onClick={() => void confirm(item)}>Подтвердить</Button>
    if (item.kind === 'PAYMENT') return <Button size="small" variant="contained" aria-label={`Открыть кассу ${item.patientName}`} onClick={onOpenCashier}>Касса</Button>
    if (item.kind === 'REBOOK') return <Button size="small" variant="contained" aria-label={`Записать снова ${item.patientName}`} onClick={() => onRebook(item)}>Записать</Button>
    return <Button size="small" variant="outlined" aria-label={`Открыть карточку ${item.patientName}`} onClick={() => onOpenPatient(item.patientId)}>Карточка</Button>
  }

  return <Box className="panel work-queue" component="section" aria-label="Требуют действия" sx={{ mb: 2 }}>
    <Box className="panel-title-row">
      <Box><Typography className="panel-label">ТРЕБУЮТ ДЕЙСТВИЯ</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Рабочая очередь на сегодня: выполненные действия исчезают из списка</Typography></Box>
      <Chip size="small" label={items.length} color={items.length > 0 ? 'primary' : 'default'} variant="outlined" />
    </Box>
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ my: 1.5, alignItems: { sm: 'center' } }}>
      <TextField select size="small" label="Очередь" value={filter} onChange={(event) => selectFilter(event.target.value as QueueFilter)} sx={{ minWidth: 210 }}>
        <MenuItem value="ALL">Все действия · {items.length}</MenuItem>
        {kindOrder.map((kind) => <MenuItem key={kind} value={kind}>{kindLabels[kind]} · {items.filter((item) => item.kind === kind).length}</MenuItem>)}
      </TextField>
      {filter !== 'ALL' && <Button size="small" onClick={() => selectFilter('ALL')}>Сбросить фильтр</Button>}
      {apiOnline && <Button size="small" onClick={() => setLocalRevision((value) => value + 1)}>Обновить</Button>}
    </Stack>
    {error && <Alert severity="warning" sx={{ mb: 1 }}>{error}</Alert>}
    {loaded === null && !error && <Box className="empty" sx={{ minHeight: 90 }}><CircularProgress size={24} aria-label="Загрузка рабочей очереди" /></Box>}
    {loaded !== null && visible.length === 0 && <Typography sx={{ color: 'text.secondary', py: 1 }}>{items.length === 0 ? 'Все рабочие ситуации на сегодня обработаны.' : 'В выбранной очереди действий нет.'}</Typography>}
    {visible.map((item) => <Box className="work-queue-row" key={item.id}>
      <Box className="work-queue-time"><Typography sx={{ fontWeight: 750 }}>{clinicParts(new Date(item.startsAt)).time}</Typography><Chip size="small" variant="outlined" label={kindLabels[item.kind]} /></Box>
      <Box sx={{ flex: 1, minWidth: 0 }}><Button variant="text" sx={{ p: 0, minWidth: 0, fontWeight: 700, textTransform: 'none' }} onClick={() => onOpenPatient(item.patientId)}>{item.patientName}</Button><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{item.serviceName} · {item.practitionerName}</Typography><Typography sx={{ fontSize: 13 }}>{item.reason}{item.amountMinor != null ? ` · ${Math.round(item.amountMinor / 100).toLocaleString('ru-RU')} ₽` : ''}</Typography></Box>
      {action(item)}
    </Box>)}
  </Box>
}
