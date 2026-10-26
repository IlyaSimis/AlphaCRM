import { useState } from 'react'
import { AddRounded, EventNoteRounded, ScheduleRounded } from '@mui/icons-material'
import { Avatar, Box, Button, Chip, Divider, IconButton, Menu, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { PETROV_ID } from '../../api/client'
import type { AppointmentStatus, Practitioner, ScheduleItem } from '../../api/types'
import { formatDay, localToday, shiftDate, shortDate, weekOf } from '../../shared/dates'
import { specialtyAndRoom } from '../../shared/practitioners'
import type { Role } from '../../shared/roles'
import { initialsOf } from '../../shared/text'
import { Metric } from '../../shared/ui'
import { AppointmentRow } from './AppointmentRow'
import type { ScheduleView } from './scheduleView'
import { availableActions } from './statusActions'
import { isDayOff, workingHoursLabel } from './workingHours'

type ScheduleProps = {
  role: Role
  doctorId: string
  date: string
  view: ScheduleView
  appointments: ScheduleItem[]
  practitioners: Practitioner[]
  doctorFilter: string
  onDoctorFilter: (id: string) => void
  apiOnline: boolean
  onDate: (date: string) => void
  onView: (view: ScheduleView) => void
  onBooking: (date?: string, practitionerId?: string) => void
  onStatus: (item: ScheduleItem, to: AppointmentStatus) => void
  onPatient: (item: ScheduleItem) => void
  onStartVisit: (item: ScheduleItem) => void
  onReschedule: (item: ScheduleItem) => void
  onCancel: (item: ScheduleItem) => void
  waitlistCount: (item: ScheduleItem) => number
  onWaitlist: (item: ScheduleItem) => void
}

const weekdayShort = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']

export function Schedule(props: ScheduleProps) {
  const { role, doctorId, date, view, appointments, practitioners, doctorFilter, onDoctorFilter, apiOnline, onDate, onView, onBooking, onStatus } = props
  const [menu, setMenu] = useState<{ anchor: HTMLElement; item: ScheduleItem } | null>(null)
  const visibleDoctors = practitioners.filter((doctor) => doctorFilter === 'all' || doctor.id === doctorFilter)
  const days = view === 'week' ? weekOf(date) : [date]
  const periodItems = appointments.filter((item) => days.includes(item.date) && visibleDoctors.some((doctor) => doctor.id === item.practitionerId))
  const active = periodItems.filter((item) => item.statusCode !== 'CANCELLED' && item.statusCode !== 'NO_SHOW')
  const cancelled = periodItems.filter((item) => item.statusCode === 'CANCELLED').length
  const waiting = active.filter((item) => item.statusCode === 'BOOKED').length
  const menuActions = menu ? availableActions(menu.item, role, doctorId) : []
  const step = view === 'week' ? 7 : 1
  const allOff = visibleDoctors.length > 0 && visibleDoctors.every((doctor) => isDayOff(doctor, date))
  const period = view === 'week' ? 'за неделю' : 'в этот день'

  const row = (item: ScheduleItem, compact: boolean) => (
    <AppointmentRow key={item.id ?? `${item.date}-${item.time}-${item.name}`} item={item} role={role} doctorId={doctorId} compact={compact} waitlistCount={props.waitlistCount(item)} onMenu={(anchor, target) => setMenu({ anchor, item: target })} onPatient={props.onPatient} onStartVisit={props.onStartVisit} onReschedule={props.onReschedule} onCancel={props.onCancel} onWaitlist={props.onWaitlist} />
  )

  const doctorHeading = (doctor: Practitioner, subtitle: string, chip?: string) => (
    <Box className="doctor-heading">
      <Avatar sx={{ bgcolor: doctor.id === PETROV_ID ? '#244d48' : '#3d4f7a' }}>{initialsOf(doctor.fullName)}</Avatar>
      <Box><Typography sx={{ fontWeight: 750 }}>{doctor.fullName}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{specialtyAndRoom(doctor)} · {subtitle}</Typography></Box>
      {chip && <Chip label={chip} size="small" variant="outlined" color={isDayOff(doctor, date) ? 'warning' : 'default'} />}
    </Box>
  )

  return (
    <>
      <Box className="summary-grid">
        <Metric value={String(active.length)} label={`приёмов ${period}`} note="без отмен и неявок" />
        <Metric value={String(cancelled)} label="отменено" note="слот можно занять снова" />
        <Metric value={String(waiting)} label="ожидает оформления" note="статус «Ожидает»" accent={waiting > 0} />
      </Box>
      <Box className="toolbar">
        <Box className="date-nav">
          <IconButton aria-label={view === 'week' ? 'Предыдущая неделя' : 'Предыдущий день'} onClick={() => onDate(shiftDate(date, -step))}>‹</IconButton>
          <Box>
            <Typography sx={{ fontWeight: 750 }}>{view === 'week' ? `Неделя ${shortDate(days[0])} – ${shortDate(days[6])}` : formatDay(date)}</Typography>
            <TextField size="small" type="date" value={date} onChange={(event) => { if (event.target.value) onDate(event.target.value) }} slotProps={{ htmlInput: { 'aria-label': 'День расписания' } }} />
          </Box>
          <IconButton aria-label={view === 'week' ? 'Следующая неделя' : 'Следующий день'} onClick={() => onDate(shiftDate(date, step))}>›</IconButton>
          <ToggleButtonGroup size="small" exclusive value={view} onChange={(_event: unknown, next: ScheduleView | null) => { if (next) onView(next) }} aria-label="Вид расписания">
            <ToggleButton value="day">День</ToggleButton>
            <ToggleButton value="week">Неделя</ToggleButton>
          </ToggleButtonGroup>
          <TextField select size="small" label="Врач" value={doctorFilter} onChange={(event) => onDoctorFilter(event.target.value)} sx={{ minWidth: 190 }}>
            <MenuItem value="all">Все врачи</MenuItem>
            {practitioners.map((doctor) => <MenuItem key={doctor.id} value={doctor.id}>{doctor.fullName}</MenuItem>)}
          </TextField>
        </Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}><Chip size="small" label={apiOnline ? 'API подключён' : 'Демо-режим'} color={apiOnline ? 'success' : 'default'} variant="outlined" /><Button variant={date === localToday() ? 'outlined' : 'contained'} startIcon={<ScheduleRounded />} onClick={() => onDate(localToday())}>Сегодня</Button><Button variant="contained" startIcon={<AddRounded />} onClick={() => onBooking()}>Новая запись</Button></Stack>
      </Box>
      {view === 'day' && (periodItems.length === 0 ? (
        <Box className="schedule-card"><Box className="empty" sx={{ minHeight: 160 }}><EventNoteRounded /><Typography sx={{ fontWeight: 700 }}>На этот день записей нет</Typography>{allOff && <Typography sx={{ color: 'text.secondary' }}>Выходной: врачи не принимают</Typography>}<Button size="small" disabled={allOff} onClick={() => onBooking(date)}>{allOff ? 'Выходной — запись недоступна' : '+ Записать на свободное время'}</Button></Box></Box>
      ) : visibleDoctors.map((doctor) => {
        const items = periodItems.filter((item) => item.practitionerId === doctor.id)
        const dayOff = isDayOff(doctor, date)
        return (
          <Box className="schedule-card" key={doctor.id}>
            {doctorHeading(doctor, formatDay(date), workingHoursLabel(doctor, date))}
            <Box className="appointment-list">
              {items.length === 0 && <Typography sx={{ color: 'text.secondary', py: 2, px: 1 }}>{dayOff ? 'Выходной: врач не принимает' : 'Записей к этому врачу нет'}</Typography>}
              {items.map((item) => row(item, false))}
              <Box className="free-slot"><Typography sx={{ color: 'text.secondary' }}>—</Typography><Divider /><Button size="small" disabled={dayOff} aria-label={dayOff ? `Выходной у врача ${doctor.fullName}: запись недоступна` : undefined} onClick={() => onBooking(date, doctor.id)}>{dayOff ? 'Выходной — запись недоступна' : '+ Записать на свободное время'}</Button></Box>
            </Box>
          </Box>
        )
      }))}
      {view === 'week' && visibleDoctors.map((doctor) => (
        <Box className="schedule-card" key={doctor.id}>
          {doctorHeading(doctor, `неделя ${shortDate(days[0])} – ${shortDate(days[6])}`)}
          <Box className="week-grid">
            {days.map((day, index) => {
              const items = periodItems.filter((item) => item.practitionerId === doctor.id && item.date === day)
              const dayOff = isDayOff(doctor, day)
              return (
                <Box className={`week-day ${dayOff ? 'day-off' : ''} ${day === date ? 'selected' : ''}`} key={day} aria-label={`${doctor.fullName}, ${weekdayShort[index]} ${shortDate(day)}`} role="group">
                  <Box className="week-day-head">
                    <Button size="small" onClick={() => { onDate(day); onView('day') }}>{weekdayShort[index]} {shortDate(day)}</Button>
                    <Typography sx={{ fontSize: 12, color: dayOff ? 'warning.main' : 'text.secondary' }}>{workingHoursLabel(doctor, day)}</Typography>
                  </Box>
                  {items.map((item) => row(item, true))}
                  {!dayOff && items.length === 0 && <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Свободно</Typography>}
                  <Button size="small" disabled={dayOff} onClick={() => onBooking(day, doctor.id)}>{dayOff ? 'Не принимает' : '+ Запись'}</Button>
                </Box>
              )
            })}
          </Box>
        </Box>
      ))}
      <Menu anchorEl={menu?.anchor} open={Boolean(menu)} onClose={() => setMenu(null)}>
        {menuActions.map((action) => <MenuItem key={action.to} onClick={() => { const target = menu?.item; setMenu(null); if (target) onStatus(target, action.to) }}>{action.label}</MenuItem>)}
      </Menu>
    </>
  )
}
