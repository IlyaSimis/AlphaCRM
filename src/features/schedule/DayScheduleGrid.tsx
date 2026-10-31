import { CancelRounded, EditCalendarRounded, MoreHorizRounded, PlayArrowRounded, PlaylistAddCheckRounded } from '@mui/icons-material'
import { Box, Button, Chip, IconButton, Tooltip, Typography } from '@mui/material'
import { CANCELLABLE } from '../../api/schedule'
import type { Practitioner, ScheduleItem } from '../../api/types'
import { isoWeekday } from '../../shared/dates'
import { canWriteAppointments, type Role } from '../../shared/roles'
import { specialtyAndRoom } from '../../shared/practitioners'
import { RESCHEDULABLE, availableActions, canStartVisit } from './statusActions'
import { isDayOff, workingHoursLabel } from './workingHours'

const SLOT_MINUTES = 30
const ROW_HEIGHT = 48

function minutesOf(time: string) {
  const [hours, minutes] = time.split(':').map(Number)
  return hours * 60 + minutes
}

function timeOf(minutes: number) {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
}

type DayScheduleGridProps = {
  date: string
  doctors: Practitioner[]
  appointments: ScheduleItem[]
  role: Role
  doctorId: string
  waitlistCount: (item: ScheduleItem) => number
  onBooking: (date: string, practitionerId: string, time: string) => void
  onStatusMenu: (anchor: HTMLElement, item: ScheduleItem) => void
  onPatient: (item: ScheduleItem) => void
  onStartVisit: (item: ScheduleItem) => void
  onReschedule: (item: ScheduleItem) => void
  onCancel: (item: ScheduleItem) => void
  onWaitlist: (item: ScheduleItem) => void
}

/** Вертикальный день: общий временной ряд и отдельная колонка каждого врача. */
export function DayScheduleGrid({ date, doctors, appointments, role, doctorId, waitlistCount, onBooking, onStatusMenu, onPatient, onStartVisit, onReschedule, onCancel, onWaitlist }: DayScheduleGridProps) {
  const day = isoWeekday(date)
  const shiftStarts = doctors.flatMap((doctor) => doctor.workingHours.filter((shift) => shift.dayOfWeek === day).map((shift) => minutesOf(shift.startsAt)))
  const shiftEnds = doctors.flatMap((doctor) => doctor.workingHours.filter((shift) => shift.dayOfWeek === day).map((shift) => minutesOf(shift.endsAt)))
  const appointmentStarts = appointments.map((item) => minutesOf(item.time))
  const appointmentEnds = appointments.map((item) => minutesOf(item.time) + item.minutes)
  const start = Math.floor(Math.min(8 * 60, ...shiftStarts, ...appointmentStarts) / 60) * 60
  const end = Math.ceil(Math.max(21 * 60, ...shiftEnds, ...appointmentEnds) / 60) * 60
  const slots = Array.from({ length: Math.max(0, (end - start) / SLOT_MINUTES) }, (_, index) => start + index * SLOT_MINUTES)
  const admin = canWriteAppointments(role)

  return (
    <Box className="schedule-day-shell">
      <Box className="schedule-day-grid" sx={{ gridTemplateColumns: `72px repeat(${doctors.length}, minmax(220px, 1fr))`, minWidth: 72 + doctors.length * 220 }}>
        <Box className="schedule-grid-corner"><Typography>Время</Typography></Box>
        {doctors.map((doctor) => <Box className="schedule-doctor-head" key={doctor.id}>
          <Typography sx={{ fontWeight: 750, fontSize: 14 }}>{doctor.fullName}</Typography>
          <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{specialtyAndRoom(doctor)}</Typography>
          <Chip size="small" variant="outlined" color={isDayOff(doctor, date) ? 'warning' : 'default'} label={workingHoursLabel(doctor, date)} />
        </Box>)}
        <Box className="schedule-time-axis" sx={{ height: slots.length * ROW_HEIGHT }}>
          {slots.map((minutes, index) => <Box className="schedule-time-label" key={minutes} sx={{ top: index * ROW_HEIGHT, height: ROW_HEIGHT }}>{minutes % 60 === 0 && timeOf(minutes)}</Box>)}
        </Box>
        {doctors.map((doctor) => {
          const dayOff = isDayOff(doctor, date)
          const shifts = doctor.workingHours.filter((shift) => shift.dayOfWeek === day)
          return <Box className={`schedule-doctor-track ${dayOff ? 'day-off' : ''}`} key={doctor.id} sx={{ height: slots.length * ROW_HEIGHT }}>
            {slots.map((minutes, index) => {
              const time = timeOf(minutes)
              const withinShift = shifts.some((shift) => minutes >= minutesOf(shift.startsAt) && minutes < minutesOf(shift.endsAt))
              return <button type="button" className="schedule-empty-slot" key={minutes} disabled={dayOff || !withinShift} aria-label={`Свободное время: ${doctor.fullName}, ${time}`} style={{ top: index * ROW_HEIGHT, height: ROW_HEIGHT }} onClick={() => onBooking(date, doctor.id, time)} />
            })}
            {appointments.filter((item) => item.practitionerId === doctor.id).map((item) => {
              const top = (minutesOf(item.time) - start) / SLOT_MINUTES * ROW_HEIGHT
              const height = Math.max(38, item.minutes / SLOT_MINUTES * ROW_HEIGHT - 4)
              const freed = item.statusCode === 'CANCELLED' || item.statusCode === 'NO_SHOW'
              return <Box className={`schedule-appointment ${item.tone} ${item.minutes <= 30 ? 'compact' : item.minutes < 60 ? 'short' : ''}`} key={item.id ?? `${item.time}-${item.name}`} sx={{ top: Math.max(2, top + 2), height }}>
                <Box className="schedule-appointment-head">
                  <Box component="span" sx={{ display: 'flex', gap: 0.5, alignItems: 'baseline', fontWeight: 800, fontSize: 12 }}><Typography component="span" sx={{ font: 'inherit' }}>{item.time}</Typography><Typography component="span" sx={{ fontSize: 11 }}>· {item.duration}</Typography></Box>
                  {availableActions(item, role, doctorId).length > 0 && <IconButton size="small" aria-label={`Изменить статус ${item.time} ${item.name}`} onClick={(event) => onStatusMenu(event.currentTarget, item)}><MoreHorizRounded /></IconButton>}
                </Box>
                <Button className="schedule-patient-name" onClick={() => onPatient(item)}>{item.name}</Button>
                <Typography className="schedule-service-name" title={item.type}>{item.type}</Typography>
                <Box className="schedule-appointment-bottom">
                  <Chip size="small" className={`status-chip ${item.tone}`} label={item.status} />
                  <Box className="schedule-appointment-actions">
                    {canStartVisit(item, role, doctorId) && <Tooltip title="Начать приём"><IconButton size="small" aria-label={`Начать приём ${item.time} ${item.name}`} onClick={() => onStartVisit(item)}><PlayArrowRounded /></IconButton></Tooltip>}
                    {admin && RESCHEDULABLE.includes(item.statusCode) && <Tooltip title="Перенести"><IconButton size="small" aria-label={`Перенести запись ${item.time} ${item.name}`} onClick={() => onReschedule(item)}><EditCalendarRounded /></IconButton></Tooltip>}
                    {admin && CANCELLABLE.includes(item.statusCode) && <Tooltip title="Отменить"><IconButton size="small" aria-label={`Отменить запись ${item.time} ${item.name}`} onClick={() => onCancel(item)}><CancelRounded /></IconButton></Tooltip>}
                    {admin && freed && waitlistCount(item) > 0 && <Tooltip title={`Ждут: ${waitlistCount(item)}`}><IconButton size="small" aria-label={`Лист ожидания на слот ${item.date} ${item.time}`} onClick={() => onWaitlist(item)}><PlaylistAddCheckRounded /></IconButton></Tooltip>}
                  </Box>
                </Box>
              </Box>
            })}
          </Box>
        })}
      </Box>
    </Box>
  )
}
