import { useEffect, useState } from 'react'
import { AddRounded, PaymentsRounded, PersonAddAlt1Rounded } from '@mui/icons-material'
import { Box, Button, Stack, Typography } from '@mui/material'
import { loadCharges } from '../../api/billing'
import { loadOutbox } from '../../api/outbox'
import { loadAppointments } from '../../api/schedule'
import type { ChargeItem, OutboxItem, PatientItem, Practitioner, ScheduleItem, TaskItem } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { formatDay, localToday, shiftDate, shortDate } from '../../shared/dates'
import type { Role } from '../../shared/roles'
import { buildCashierRows, stateLabels, type CashierRow } from '../cashier/cashier'
import { channelLabels, reasonText, templateLabels } from '../outbox/outboxLabels'
import { formatMoney } from '../schedule/booking'
import { overdueTasks, rowAmount, todayActions, todayBlocks, unpaidRows, upcomingAppointments, UNPAID_PERIOD_DAYS } from './today'
import { TodayBlock } from './TodayBlock'

type TodayPageProps = {
  role: Role
  /** Кто вошёл — показывается рядом с датой. */
  selfName: string
  apiOnline: boolean
  practitioners: Practitioner[]
  patients: PatientItem[]
  /** Демо-режим: записи и счета из памяти интерфейса. */
  localAppointments: ScheduleItem[]
  localCharges: ChargeItem[]
  /** Открытые задачи со сроком до сегодня включительно — их уже загрузила регистратура. */
  dueTasks: TaskItem[]
  tasksError: string
  staffName: (id: string) => string
  onPatient: (item: ScheduleItem) => void
  onOpen: (page: 'registry' | 'cashier' | 'outbox') => void
  /** Те же сценарии, что на расписании и в списке пациентов, — не отдельные мастера. */
  onNewAppointment: () => void
  onNewPatient: () => void
}

type Loaded<T> = { items: T[] | null; error: string }
const pending = <T,>(): Loaded<T> => ({ items: null, error: '' })

/**
 * Рабочий день администратора, кассира и менеджера: ближайшие записи, просроченные задачи,
 * неоплаченные приёмы и ошибки доставки. Каждый блок — только роли с доступом к этим данным.
 */
export function TodayPage(props: TodayPageProps) {
  const { role, apiOnline, practitioners, patients } = props
  const blocks = todayBlocks(role)
  const actions = todayActions(role)
  const today = localToday()
  const tomorrow = shiftDate(today, 1)
  const [appointments, setAppointments] = useState<Loaded<ScheduleItem>>(pending)
  const [billing, setBilling] = useState<Loaded<CashierRow>>(pending)
  const [failed, setFailed] = useState<Loaded<OutboxItem>>(pending)

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    const fail = (set: (value: Loaded<never>) => void) => (reason: Error) => { if (!cancelled) set({ items: null, error: reason.message }) }
    if (blocks.appointments) {
      loadAppointments({ from: today, to: tomorrow })
        .then((items) => { if (!cancelled) setAppointments({ items, error: '' }) })
        .catch(fail(setAppointments))
    }
    if (blocks.unpaid) {
      Promise.all([loadAppointments({ from: shiftDate(today, -UNPAID_PERIOD_DAYS), to: today, status: 'COMPLETED' }), loadCharges()])
        .then(([completed, charges]) => { if (!cancelled) setBilling({ items: buildCashierRows(completed, charges), error: '' }) })
        .catch(fail(setBilling))
    }
    if (blocks.failed) {
      loadOutbox('FAILED')
        .then((items) => { if (!cancelled) setFailed({ items, error: '' }) })
        .catch(fail(setFailed))
    }
    return () => { cancelled = true }
  }, [apiOnline, today, tomorrow, blocks.appointments, blocks.unpaid, blocks.failed])

  const doctorName = (id: string) => practitioners.find((doctor) => doctor.id === id)?.fullName ?? 'Врач'
  const patientName = (id: string | null) => (id ? patients.find((patient) => patient.id === id)?.name : null) ?? null
  const scheduled = apiOnline ? appointments.items : props.localAppointments
  const rows = apiOnline ? billing.items : buildCashierRows(props.localAppointments.filter((item) => item.statusCode === 'COMPLETED' && item.date >= shiftDate(today, -UNPAID_PERIOD_DAYS) && item.date <= today), props.localCharges)
  const upcoming = upcomingAppointments(scheduled ?? [], today, tomorrow)
  const overdue = overdueTasks(props.dueTasks, today)
  const unpaid = unpaidRows(rows ?? [])
  const failures = apiOnline ? failed.items ?? [] : []

  return (
    <>
      <Box className="section-intro today-intro">
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1.5, flexWrap: 'wrap' }}>
            <Typography variant="h4" sx={{ fontWeight: 750 }}>{formatDay(today)}</Typography>
            <Typography sx={{ color: 'text.secondary', fontWeight: 650 }}>{props.selfName}</Typography>
          </Box>
          <Typography sx={{ color: 'text.secondary' }}>Что требует внимания: ближайшие записи и незакрытые дела.</Typography>
          {(actions.booking || actions.patient || actions.cashier) && <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1.5, flexWrap: 'wrap' }} role="group" aria-label="Быстрые действия">
            {actions.booking && <Button variant="contained" startIcon={<AddRounded />} onClick={props.onNewAppointment}>Новая запись</Button>}
            {actions.patient && <Button variant="outlined" startIcon={<PersonAddAlt1Rounded />} onClick={props.onNewPatient}>Новый пациент</Button>}
            {actions.cashier && <Button variant="outlined" startIcon={<PaymentsRounded />} onClick={() => props.onOpen('cashier')}>Касса</Button>}
          </Stack>}
        </Box>
      </Box>
      <Box className="today-grid">
        {blocks.appointments && <TodayBlock title="Записи на сегодня и завтра" empty="Записей на сегодня и завтра нет" count={upcoming.length} error={apiOnline ? appointments.error : ''} loading={apiOnline && appointments.items === null && !appointments.error}>
          {upcoming.map((item) => (
            <Box component="button" type="button" className="today-row" key={item.id ?? `${item.date}-${item.time}-${item.name}`} onClick={() => props.onPatient(item)} aria-label={`Открыть карточку: ${item.name}, ${shortDate(item.date)} ${item.time}`}>
              <Typography sx={{ fontWeight: 700, minWidth: 92 }}>{item.date === today ? 'сегодня' : 'завтра'} {item.time}</Typography>
              <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 650 }}>{item.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{doctorName(item.practitionerId)} · {item.type}</Typography></Box>
              <Typography sx={{ fontSize: 13 }}>{item.status}</Typography>
            </Box>
          ))}
        </TodayBlock>}
        {blocks.tasks && <TodayBlock title="Просроченные задачи" empty="Просроченных задач нет" count={overdue.length} error={props.tasksError} more={{ label: 'Все задачи', onClick: () => props.onOpen('registry') }}>
          {overdue.map((task) => (
            <Box component="button" type="button" className="today-row" key={task.id} onClick={() => props.onOpen('registry')} aria-label={`Просроченная задача: ${task.title}`}>
              <Typography sx={{ fontWeight: 700, minWidth: 92, color: 'error.main' }}>до {shortDate(task.dueDate)}</Typography>
              <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 650 }}>{task.title}{patientName(task.patientId) ? ` · ${patientName(task.patientId)}` : ''}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{props.staffName(task.assigneeId)}</Typography></Box>
            </Box>
          ))}
        </TodayBlock>}
        {blocks.unpaid && <TodayBlock title="Завершены и не оплачены" empty="Неоплаченных приёмов нет" count={unpaid.length} error={apiOnline ? billing.error : ''} loading={apiOnline && billing.items === null && !billing.error} more={{ label: 'Все счета', onClick: () => props.onOpen('cashier') }}>
          {unpaid.map((row) => {
            const amount = rowAmount(row)
            return (
              <Box component="button" type="button" className="today-row" key={row.appointment.id} onClick={() => props.onOpen('cashier')} aria-label={`Неоплаченный приём: ${row.appointment.name}, ${shortDate(row.appointment.date)}`}>
                <Typography sx={{ fontWeight: 700, minWidth: 92 }}>{amount == null ? '—' : formatMoney(amount, row.charge?.currency ?? row.appointment.currency ?? 'RUB')}</Typography>
                <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 650 }}>{row.appointment.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{shortDate(row.appointment.date)} · {stateLabels[row.state]}</Typography></Box>
              </Box>
            )
          })}
        </TodayBlock>}
        {blocks.failed && <TodayBlock title="Ошибки доставки" empty={apiOnline ? 'Ошибок доставки нет' : 'Демо-песочница ничего наружу не отправляет'} count={failures.length} error={apiOnline ? failed.error : ''} loading={apiOnline && failed.items === null && !failed.error} more={{ label: 'Все выводы', onClick: () => props.onOpen('outbox') }}>
          {failures.map((item) => (
            <Box component="button" type="button" className="today-row" key={item.id} onClick={() => props.onOpen('outbox')} aria-label={`Ошибка доставки: ${templateLabels[item.template] ?? item.template}`}>
              <Typography sx={{ fontWeight: 700, minWidth: 92 }}>{formatClinicMoment(item.lastAttemptAt ?? item.createdAt)}</Typography>
              <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 650 }}>{templateLabels[item.template] ?? item.template} · {item.channel ? channelLabels[item.channel] ?? item.channel : 'канал не выбран'}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{reasonText(item.reason) || 'причина не указана'}</Typography></Box>
            </Box>
          ))}
        </TodayBlock>}
      </Box>
    </>
  )
}
