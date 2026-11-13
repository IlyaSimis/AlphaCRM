import { ChevronRightRounded, MoreHorizRounded, PlaylistAddCheckRounded } from '@mui/icons-material'
import { Box, Button, Chip, IconButton, Stack, Typography } from '@mui/material'
import { CANCELLABLE } from '../../api/schedule'
import type { ScheduleItem } from '../../api/types'
import { canWriteAppointments, type Role } from '../../shared/roles'
import { RESCHEDULABLE, availableActions, canStartVisit } from './statusActions'

type AppointmentRowProps = {
  item: ScheduleItem
  role: Role
  doctorId: string
  /** Неделя: узкая колонка, те же действия, что и в дне. */
  compact?: boolean
  /** Сколько ожидающих подходит под освободившийся слот. */
  waitlistCount: number
  onMenu: (anchor: HTMLElement, item: ScheduleItem) => void
  onPatient: (item: ScheduleItem) => void
  onStartVisit: (item: ScheduleItem) => void
  onReschedule: (item: ScheduleItem) => void
  onCancel: (item: ScheduleItem) => void
  onWaitlist: (item: ScheduleItem) => void
}

export function AppointmentRow({ item, role, doctorId, compact = false, waitlistCount, onMenu, onPatient, onStartVisit, onReschedule, onCancel, onWaitlist }: AppointmentRowProps) {
  // Перенос, отмена и лист ожидания — у регистратуры (schedule.appointment.write).
  const admin = canWriteAppointments(role)
  const freed = item.statusCode === 'CANCELLED' || item.statusCode === 'NO_SHOW'
  return (
    <Box className={`appointment ${compact ? 'compact' : ''}`}>
      <Box className="time"><Typography sx={{ fontWeight: 750 }}>{item.time}</Typography><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{item.duration}</Typography></Box>
      <Box className={`status-line ${item.tone}`} />
      <Box className="appointment-info" onClick={() => onPatient(item)} role="button" tabIndex={0}>
        <Typography sx={{ fontWeight: 700 }}>{item.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{item.type}</Typography>
      </Box>
      <Chip label={item.status} size="small" className={`status-chip ${item.tone}`} />
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        {canStartVisit(item, role, doctorId) ? <Button size="small" variant="contained" aria-label={`Начать приём ${item.time} ${item.name}`} onClick={() => onStartVisit(item)}>Начать приём</Button> : <IconButton size="small" aria-label={`Открыть карточку ${item.name}`} onClick={() => onPatient(item)}><ChevronRightRounded /></IconButton>}
        {availableActions(item, role, doctorId).length > 0 && <IconButton size="small" title="Изменить статус" aria-label={`Изменить статус ${item.time} ${item.name}`} onClick={(event) => onMenu(event.currentTarget, item)}><MoreHorizRounded /></IconButton>}
        {admin && RESCHEDULABLE.includes(item.statusCode) && <Button size="small" variant="text" aria-label={`Перенести запись ${item.time} ${item.name}`} onClick={() => onReschedule(item)}>Перенести</Button>}
        {admin && CANCELLABLE.includes(item.statusCode) && <Button size="small" variant="outlined" aria-label={`Отменить запись ${item.time} ${item.name}`} onClick={() => onCancel(item)}>Отменить</Button>}
        {admin && freed && waitlistCount > 0 && <Button size="small" startIcon={<PlaylistAddCheckRounded />} aria-label={`Лист ожидания на слот ${item.date} ${item.time}`} onClick={() => onWaitlist(item)}>Ждут: {waitlistCount}</Button>}
      </Stack>
    </Box>
  )
}
