import { useState } from 'react'
import { AddRounded, ChevronRightRounded, EventNoteRounded, MoreHorizRounded, ScheduleRounded } from '@mui/icons-material'
import { Avatar, Box, Button, Chip, Divider, IconButton, Menu, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { PETROV_ID } from '../../api/client'
import { CANCELLABLE } from '../../api/schedule'
import type { AppointmentStatus, Practitioner, ScheduleItem } from '../../api/types'
import { formatDay, localToday, shiftDate } from '../../shared/dates'
import type { Role } from '../../shared/roles'
import { initialsOf } from '../../shared/text'
import { Metric } from '../../shared/ui'
import { RESCHEDULABLE, availableActions, canStartVisit } from './statusActions'
import { workingHoursLabel } from './workingHours'

type ScheduleProps = {
  role: Role
  doctorId: string
  date: string
  appointments: ScheduleItem[]
  practitioners: Practitioner[]
  doctorFilter: string
  onDoctorFilter: (id: string) => void
  apiOnline: boolean
  onDate: (date: string) => void
  onBooking: () => void
  onStatus: (item: ScheduleItem, to: AppointmentStatus) => void
  onPatient: (item: ScheduleItem) => void
  onStartVisit: (item: ScheduleItem) => void
  onReschedule: (item: ScheduleItem) => void
  onCancel: (item: ScheduleItem) => void
}

export function Schedule({ role, doctorId, date, appointments, practitioners, doctorFilter, onDoctorFilter, apiOnline, onDate, onBooking, onStatus, onPatient, onStartVisit, onReschedule, onCancel }: ScheduleProps) {
  const [menu, setMenu] = useState<{ anchor: HTMLElement; item: ScheduleItem } | null>(null)
  const visibleDoctors = practitioners.filter((doctor) => doctorFilter === 'all' || doctor.id === doctorFilter)
  const dayItems = appointments.filter((item) => item.date === date && visibleDoctors.some((doctor) => doctor.id === item.practitionerId))
  const active = dayItems.filter((item) => item.statusCode !== 'CANCELLED' && item.statusCode !== 'NO_SHOW')
  const cancelled = dayItems.filter((item) => item.statusCode === 'CANCELLED').length
  const waiting = active.filter((item) => item.statusCode === 'BOOKED').length
  const menuActions = menu ? availableActions(menu.item, role, doctorId) : []
  return (
    <>
      <Box className="summary-grid">
        <Metric value={String(active.length)} label="приёмов в этот день" note="без отмен и неявок" />
        <Metric value={String(cancelled)} label="отменено" note="слот можно занять снова" />
        <Metric value={String(waiting)} label="ожидает оформления" note="статус «Ожидает»" accent={waiting > 0} />
      </Box>
      <Box className="toolbar">
        <Box className="date-nav">
          <IconButton aria-label="Предыдущий день" onClick={() => onDate(shiftDate(date, -1))}>‹</IconButton>
          <Box>
            <Typography sx={{ fontWeight: 750 }}>{formatDay(date)}</Typography>
            <TextField size="small" type="date" label="День расписания" value={date} onChange={(event) => { if (event.target.value) onDate(event.target.value) }} slotProps={{ inputLabel: { shrink: true } }} />
          </Box>
          <IconButton aria-label="Следующий день" onClick={() => onDate(shiftDate(date, 1))}>›</IconButton>
          <TextField select size="small" label="Врач" value={doctorFilter} onChange={(event) => onDoctorFilter(event.target.value)} sx={{ minWidth: 190 }}>
            <MenuItem value="all">Все врачи</MenuItem>
            {practitioners.map((doctor) => <MenuItem key={doctor.id} value={doctor.id}>{doctor.fullName}</MenuItem>)}
          </TextField>
        </Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}><Chip size="small" label={apiOnline ? 'API подключён' : 'Демо-режим'} color={apiOnline ? 'success' : 'default'} variant="outlined" /><Button variant={date === localToday() ? 'outlined' : 'contained'} startIcon={<ScheduleRounded />} onClick={() => onDate(localToday())}>Сегодня</Button><Button variant="contained" startIcon={<AddRounded />} onClick={onBooking}>Новая запись</Button></Stack>
      </Box>
      {dayItems.length === 0 ? (
        <Box className="schedule-card"><Box className="empty" sx={{ minHeight: 160 }}><EventNoteRounded /><Typography sx={{ fontWeight: 700 }}>На этот день записей нет</Typography><Button size="small" onClick={onBooking}>+ Записать на свободное время</Button></Box></Box>
      ) : visibleDoctors.map((doctor) => {
        const items = dayItems.filter((item) => item.practitionerId === doctor.id)
        return (
          <Box className="schedule-card" key={doctor.id}>
            <Box className="doctor-heading">
              <Avatar sx={{ bgcolor: doctor.id === PETROV_ID ? '#244d48' : '#3d4f7a' }}>{initialsOf(doctor.fullName)}</Avatar>
              <Box><Typography sx={{ fontWeight: 750 }}>{doctor.fullName}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{doctor.specialty} · {formatDay(date)}</Typography></Box>
              <Chip label={workingHoursLabel(doctor, date)} size="small" variant="outlined" />
            </Box>
            <Box className="appointment-list">
              {items.length === 0 && <Typography sx={{ color: 'text.secondary', py: 2, px: 1 }}>Записей к этому врачу нет</Typography>}
              {items.map((item) => (
                <Box className="appointment" key={item.id ?? `${item.date}-${item.time}-${item.name}`}>
                  <Box className="time"><Typography sx={{ fontWeight: 750 }}>{item.time}</Typography><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{item.duration}</Typography></Box>
                  <Box className={`status-line ${item.tone}`} />
                  <Box className="appointment-info" onClick={() => onPatient(item)} role="button" tabIndex={0}>
                    <Typography sx={{ fontWeight: 700 }}>{item.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{item.type}</Typography>
                  </Box>
                  <Chip label={item.status} size="small" className={`status-chip ${item.tone}`} />
                  <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                    {canStartVisit(item, role, doctorId) ? <Button size="small" variant="contained" aria-label={`Начать приём ${item.time} ${item.name}`} onClick={() => onStartVisit(item)}>Начать приём</Button> : <IconButton size="small" aria-label={`Открыть карточку ${item.name}`} onClick={() => onPatient(item)}><ChevronRightRounded /></IconButton>}
                    {availableActions(item, role, doctorId).length > 0 && <IconButton size="small" title="Изменить статус" aria-label={`Изменить статус ${item.time} ${item.name}`} onClick={(event) => setMenu({ anchor: event.currentTarget, item })}><MoreHorizRounded /></IconButton>}
                    {role === 'Администратор' && RESCHEDULABLE.includes(item.statusCode) && <Button size="small" variant="text" aria-label={`Перенести запись ${item.time} ${item.name}`} onClick={() => onReschedule(item)}>Перенести</Button>}
                    {role === 'Администратор' && CANCELLABLE.includes(item.statusCode) && <Button size="small" variant="outlined" aria-label={`Отменить запись ${item.time} ${item.name}`} onClick={() => onCancel(item)}>Отменить</Button>}
                  </Stack>
                </Box>
              ))}
              <Box className="free-slot"><Typography sx={{ color: 'text.secondary' }}>—</Typography><Divider /><Button size="small" onClick={onBooking}>+ Записать на свободное время</Button></Box>
            </Box>
          </Box>
        )
      })}
      <Menu anchorEl={menu?.anchor} open={Boolean(menu)} onClose={() => setMenu(null)}>
        {menuActions.map((action) => <MenuItem key={action.to} onClick={() => { const target = menu?.item; setMenu(null); if (target) onStatus(target, action.to) }}>{action.label}</MenuItem>)}
      </Menu>
    </>
  )
}
