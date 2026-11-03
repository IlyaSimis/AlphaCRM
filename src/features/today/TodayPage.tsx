import { useState } from 'react'
import { AddRounded, PaymentsRounded, PersonAddAlt1Rounded } from '@mui/icons-material'
import { Box, Button, Stack, Typography } from '@mui/material'
import type { PatientItem, Practitioner, ScheduleItem, TaskItem, WorkQueueItem } from '../../api/types'
import type { Page } from '../../app/pages'
import { formatDay, localToday, shortDate } from '../../shared/dates'
import { greeting } from '../../shared/greeting'
import { canManageRegistry, type Role } from '../../shared/roles'
import { overdueTasks, todayActions, todayBlocks } from './today'
import { AdminWorkQueue } from './AdminWorkQueue'
import { DashboardWidgets } from './DashboardWidgets'
import { dashboardCodes } from './dashboardCodes'
import { TodayBlock } from './TodayBlock'
import { TodayNotes } from './TodayNotes'
import { TodayAppointments } from './TodayAppointments'

type TodayPageProps = {
  role: Role
  selfId: string
  /** ФИО того, кто вошёл: рядом с датой — приветствие по первому слову. */
  selfName: string
  apiOnline: boolean
  patients: PatientItem[]
  appointments: ScheduleItem[]
  practitioners: Practitioner[]
  revision: number
  /** Открытые задачи со сроком до сегодня включительно — их уже загрузила регистратура. */
  dueTasks: TaskItem[]
  tasksError: string
  staffName: (id: string) => string
  onOpen: (page: Page) => void
  onOpenPatient: (patientId: string) => void
  onRebook: (item: WorkQueueItem) => void
  /** Те же сценарии, что на расписании и в списке пациентов, — не отдельные мастера. */
  onNewAppointment: () => void
  onNewPatient: () => void
}

/** Главная: очередь администратора, персональные показатели и записи текущего дня. */
export function TodayPage(props: TodayPageProps) {
  const { role, apiOnline, patients } = props
  const blocks = todayBlocks(role)
  const actions = todayActions(role)
  const today = localToday()
  const overdue = overdueTasks(props.dueTasks, today)
  const [queueRevision, setQueueRevision] = useState(0)
  const patientName = (id: string | null) => (id ? patients.find((patient) => patient.id === id)?.name : null) ?? null

  return (
    <>
      <Box className="section-intro today-intro">
        <Box>
          <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1.5, flexWrap: 'wrap' }}>
            <Typography variant="h4" sx={{ fontWeight: 750 }}>{formatDay(today)}</Typography>
            <Typography className="greeting greeting--large">{greeting(props.selfName)}</Typography>
          </Box>
          {(actions.booking || actions.patient || actions.cashier) && <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 2, flexWrap: 'wrap' }} role="group" aria-label="Быстрые действия">
            {actions.booking && <Button variant="contained" startIcon={<AddRounded />} onClick={props.onNewAppointment}>Новая запись</Button>}
            {actions.patient && <Button variant="outlined" startIcon={<PersonAddAlt1Rounded />} onClick={props.onNewPatient}>Новый пациент</Button>}
            {actions.cashier && <Button variant="outlined" startIcon={<PaymentsRounded />} onClick={() => props.onOpen('cashier')}>Касса</Button>}
          </Stack>}
        </Box>
      </Box>
      {canManageRegistry(role) && <AdminWorkQueue key={props.selfId} selfId={props.selfId} apiOnline={apiOnline} appointments={props.appointments} practitioners={props.practitioners} revision={props.revision} onOpenPatient={props.onOpenPatient} onOpenCashier={() => props.onOpen('cashier')} onRebook={props.onRebook} onChanged={() => setQueueRevision((value) => value + 1)} />}
      <Box className="today-grid">
        <Box className="today-column">
          <TodayNotes apiOnline={apiOnline} selfId={props.selfId} onOpen={() => props.onOpen('workspace')} />
          <DashboardWidgets apiOnline={apiOnline} role={role} codes={dashboardCodes(role)} stacked onOpen={props.onOpen} />
        </Box>
        <Box className="today-column">
          {blocks.tasks && <TodayBlock title="Просроченные задачи" empty="Просроченных задач нет" count={overdue.length} error={props.tasksError} more={{ label: 'Все задачи', onClick: () => props.onOpen('registry') }}>
            {overdue.map((task) => (
              <Box component="button" type="button" className="today-row" key={task.id} onClick={() => props.onOpen('registry')} aria-label={`Просроченная задача: ${task.title}`}>
                <Typography sx={{ fontWeight: 700, minWidth: 92, color: 'error.main' }}>до {shortDate(task.dueDate)}</Typography>
                <Box sx={{ flex: 1, minWidth: 0 }}><Typography sx={{ fontWeight: 650 }}>{task.title}{patientName(task.patientId) ? ` · ${patientName(task.patientId)}` : ''}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{props.staffName(task.assigneeId)}</Typography></Box>
              </Box>
            ))}
          </TodayBlock>}
          <TodayAppointments apiOnline={apiOnline} appointments={props.appointments} practitioners={props.practitioners} revision={props.revision + queueRevision} onOpen={() => props.onOpen('schedule')} />
        </Box>
      </Box>
    </>
  )
}
