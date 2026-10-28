import { useState } from 'react'
import { AddRounded } from '@mui/icons-material'
import { Alert, Box, Button, Typography } from '@mui/material'
import type { PatientItem, Practitioner, ServiceItem, StaffItem } from '../../api/types'
import { localToday } from '../../shared/dates'
import { canManageRegistry, canSeeWaitlist } from '../../shared/roles'
import { periodLabel } from './registry'
import { TaskDialog } from './TaskDialog'
import { TaskList } from './TaskList'
import type { Registry } from './useRegistry'
import { WaitlistDialog } from './WaitlistDialog'
import { rolesOf } from '../../shared/permissions'

type RegistryPageProps = {
  staff: StaffItem
  registry: Registry
  patients: PatientItem[]
  practitioners: Practitioner[]
  services: ServiceItem[]
}

/** Регистратура: задачи на сегодня и лист ожидания. Сообщения пациентам отсюда не отправляются. */
export function RegistryPage({ staff, registry, patients, practitioners, services }: RegistryPageProps) {
  const role = rolesOf(staff)
  const admin = canManageRegistry(role)
  const today = localToday()
  const [taskDialog, setTaskDialog] = useState(false)
  const [taskError, setTaskError] = useState('')
  const [waitDialog, setWaitDialog] = useState(false)
  const [waitError, setWaitError] = useState('')
  const patientName = (id: string) => patients.find((item) => item.id === id)?.name ?? 'Пациент'
  const doctorName = (id: string) => practitioners.find((item) => item.id === id)?.fullName ?? 'Врач'
  const serviceName = (id: string) => services.find((item) => item.id === id)?.name ?? 'Услуга'

  return (
    <>
      <Box className="section-intro">
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Регистратура</Typography><Typography sx={{ color: 'text.secondary' }}>Внутренние задачи сотрудников и лист ожидания. Пациентам ничего не отправляется.</Typography></Box>
      </Box>
      {registry.error && <Alert severity="warning" sx={{ mb: 2 }}>{registry.error}</Alert>}
      <Box className="patient-columns">
        <Box className="panel">
          <Box className="panel-title-row">
            <Typography className="panel-label">ЗАДАЧИ НА СЕГОДНЯ</Typography>
            {admin && <Button size="small" startIcon={<AddRounded />} onClick={() => { setTaskError(''); setTaskDialog(true) }}>Новая задача</Button>}
          </Box>
          <TaskList tasks={registry.todayTasks} today={today} emptyText="На сегодня задач нет" patientName={patientName} staffName={registry.staffName} canClose={registry.canClose} onToggle={(task) => void registry.toggleTask(task)} />
        </Box>
        {canSeeWaitlist(role) && <Box className="panel">
          <Box className="panel-title-row">
            <Typography className="panel-label">ЛИСТ ОЖИДАНИЯ</Typography>
            {admin && <Button size="small" startIcon={<AddRounded />} onClick={() => { setWaitError(''); setWaitDialog(true) }}>Добавить</Button>}
          </Box>
          {registry.waitlist.length === 0 && <Typography sx={{ color: 'text.secondary', mt: 1 }}>Никто не ждёт</Typography>}
          {registry.waitlist.map((entry) => (
            <Box className="history-item" key={entry.id}>
              <Box>
                <Typography sx={{ fontWeight: 650 }}>{patientName(entry.patientId)}</Typography>
                <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{doctorName(entry.practitionerId)} · {serviceName(entry.serviceId)} · {periodLabel(entry.wishFrom, entry.wishTo)}</Typography>
              </Box>
              {admin && <Button size="small" color="warning" aria-label={`Убрать из листа ожидания ${patientName(entry.patientId)}`} onClick={() => void registry.removeWaiting(entry)}>Убрать</Button>}
            </Box>
          ))}
          <Typography sx={{ fontSize: 13, color: 'text.secondary', mt: 2 }}>Когда запись отменяют или пациент не приходит, в расписании у свободного слота появится кнопка листа ожидания.</Typography>
        </Box>}
      </Box>
      {taskDialog && <TaskDialog patients={patients} staff={registry.staffList} defaultAssigneeId={staff.id} today={today} error={taskError} onClose={() => setTaskDialog(false)} onSave={async (draft) => {
        const error = await registry.addTask(draft)
        if (error) setTaskError(error)
        else setTaskDialog(false)
      }} />}
      {waitDialog && <WaitlistDialog patients={patients} practitioners={practitioners} services={services} today={today} error={waitError} onClose={() => setWaitDialog(false)} onSave={async (draft) => {
        const error = await registry.addWaiting(draft)
        if (error) setWaitError(error)
        else setWaitDialog(false)
      }} />}
    </>
  )
}
