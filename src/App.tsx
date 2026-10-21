import { useEffect, useMemo, useRef, useState } from 'react'
import {
  AddRounded,
  CalendarMonthRounded,
  CheckCircleRounded,
  ChevronRightRounded,
  CloseRounded,
  EventNoteRounded,
  HealthAndSafetyRounded,
  LockOutlined,
  DarkModeRounded,
  EditRounded,
  HistoryRounded,
  InsightsRounded,
  LightModeRounded,
  MoreHorizRounded,
  MenuRounded,
  PeopleAltRounded,
  PersonSearchRounded,
  ScheduleRounded,
  SearchRounded,
  ShieldOutlined,
  MedicalServicesRounded,
} from '@mui/icons-material'
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Drawer,
  FormControl,
  IconButton,
  InputAdornment,
  Menu,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import {
  ApiError,
  LEBEDEVA_ID,
  PETROV_ID,
  cancelAppointment,
  changeAppointmentStatus,
  createAppointment,
  createPatient,
  demoPractitioners,
  loadPatients,
  loadPractitioners,
  loadSchedule,
  rememberPatient,
  signDemoVisit,
  statusLabel,
  toneFor,
  updatePatient,
  type ApiRole,
  type AppointmentStatus,
  type PatientItem,
  type Practitioner,
  type ScheduleItem,
} from './api'
import { Analytics } from './Analytics'
import { AuditLog } from './AuditLog'
import { useColorMode } from './theme'

type Page = 'schedule' | 'patients' | 'patient' | 'visit' | 'analytics' | 'audit'
type Role = 'Администратор' | 'Врач' | 'Менеджер'
type Patient = PatientItem

const apiRoles: Record<Role, ApiRole> = { Администратор: 'ADMINISTRATOR', Врач: 'DOCTOR', Менеджер: 'MANAGER' }
const profiles: Record<Role, { name: string; initials: string }> = {
  Администратор: { name: 'Елена Смирнова', initials: 'ЕС' },
  Врач: { name: 'Александр Петров', initials: 'АП' },
  Менеджер: { name: 'Игорь Кузнецов', initials: 'ИК' },
}

/** Действия со статусом записи: какие переходы доступны и какой роли. Зеркалит AppointmentStatusPolicy на backend. */
const statusActions: Array<{ to: AppointmentStatus; label: string; from: AppointmentStatus[]; role: Role }> = [
  { to: 'CONFIRMED', label: 'Подтвердить запись', from: ['BOOKED'], role: 'Администратор' },
  { to: 'ARRIVED', label: 'Пациент пришёл', from: ['BOOKED', 'CONFIRMED'], role: 'Администратор' },
  { to: 'NO_SHOW', label: 'Отметить неявку', from: ['BOOKED', 'CONFIRMED'], role: 'Администратор' },
  { to: 'IN_PROGRESS', label: 'Пациент на приёме', from: ['ARRIVED'], role: 'Врач' },
  { to: 'COMPLETED', label: 'Завершить приём', from: ['ARRIVED', 'IN_PROGRESS'], role: 'Врач' },
]

function availableActions(item: ScheduleItem, role: Role) {
  if (role === 'Врач' && item.practitionerId !== PETROV_ID) return []
  return statusActions.filter((action) => action.role === role && action.from.includes(item.statusCode))
}

function demoItem(id: string, date: string, time: string, minutes: number, name: string, type: string, statusCode: AppointmentStatus, practitionerId = PETROV_ID): ScheduleItem {
  return { id, date, time, duration: `${minutes} мин`, name, type, status: statusLabel(statusCode), statusCode, tone: toneFor(statusCode), practitionerId }
}

const clinicDay = '2026-10-20'

const demoAppointments: ScheduleItem[] = [
  demoItem('demo-301', clinicDay, '09:00', 50, 'Анна Михайлова', 'Первичный приём', 'ARRIVED'),
  demoItem('demo-302', clinicDay, '10:10', 30, 'Дмитрий Волков', 'Повторный приём', 'CONFIRMED'),
  demoItem('demo-303', clinicDay, '11:00', 50, 'Мария Соколова', 'Первичный приём', 'BOOKED'),
  demoItem('demo-304', clinicDay, '12:00', 30, 'Алексей Морозов', 'Повторный приём', 'CONFIRMED'),
  demoItem('demo-305', clinicDay, '15:00', 50, 'Ксения Орлова', 'Первичный приём', 'CONFIRMED', LEBEDEVA_ID),
  demoItem('demo-501', '2026-10-06', '09:00', 50, 'Анна Михайлова', 'Первичный приём', 'COMPLETED'),
  demoItem('demo-502', '2026-10-06', '10:10', 30, 'Дмитрий Волков', 'Повторный приём', 'COMPLETED'),
  demoItem('demo-503', '2026-10-06', '11:00', 50, 'Мария Соколова', 'Первичный приём', 'NO_SHOW', LEBEDEVA_ID),
  demoItem('demo-504', '2026-10-08', '12:00', 30, 'Анна Михайлова', 'Повторный приём', 'COMPLETED', LEBEDEVA_ID),
  demoItem('demo-505', '2026-10-08', '09:00', 30, 'Дмитрий Волков', 'Повторный приём', 'CANCELLED'),
  demoItem('demo-506', '2026-10-13', '09:00', 50, 'Мария Соколова', 'Первичный приём', 'COMPLETED'),
  demoItem('demo-507', '2026-10-13', '10:10', 30, 'Анна Михайлова', 'Повторный приём', 'COMPLETED'),
  demoItem('demo-508', '2026-10-13', '11:00', 30, 'Дмитрий Волков', 'Повторный приём', 'COMPLETED', LEBEDEVA_ID),
  demoItem('demo-509', '2026-10-15', '14:00', 30, 'Мария Соколова', 'Повторный приём', 'NO_SHOW', LEBEDEVA_ID),
  demoItem('demo-510', '2026-10-15', '09:00', 30, 'Анна Михайлова', 'Повторный приём', 'COMPLETED'),
]

const demoPatients: Patient[] = [
  { name: 'Анна Михайлова', birth: '14.05.1991', birthDate: '1991-05-14', phone: '+7 916 245-18-72', id: 'P-10482', consent: 'Действует' },
  { name: 'Дмитрий Волков', birth: '02.11.1985', birthDate: '1985-11-02', phone: '+7 903 782-44-16', id: 'P-10396', consent: 'Действует' },
  { name: 'Мария Соколова', birth: '28.02.1996', birthDate: '1996-02-28', phone: '+7 925 663-02-41', id: 'P-10251', consent: 'Требует обновления' },
]

type BookingDraft = {
  patientId: string
  date: string
  time: string
  service: 'repeat' | 'first'
  practitionerId: string
}

type PatientInput = { fullName: string; birthDate: string; phone: string }

function localPatient(id: string, input: PatientInput, consent = 'Действует'): Patient {
  return { id, name: input.fullName, birth: input.birthDate.split('-').reverse().join('.'), birthDate: input.birthDate, phone: input.phone, consent }
}

function weekday(iso: string) {
  const [year, month, day] = iso.split('-').map(Number)
  const value = new Date(Date.UTC(year, month - 1, day)).getUTCDay()
  return value === 0 ? 7 : value
}

function workingHoursLabel(practitioner: Practitioner, date: string) {
  const hours = practitioner.workingHours.find((item) => item.dayOfWeek === weekday(date))
  return hours ? `${hours.startsAt.slice(0, 5)}–${hours.endsAt.slice(0, 5)}` : 'не принимает'
}

function initialsOf(name: string) {
  return name.split(' ').map((part) => part[0]).join('').slice(0, 2)
}

function isUuid(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
}

function sortSchedule(items: ScheduleItem[]) {
  return [...items].sort((left, right) => left.date.localeCompare(right.date) || left.time.localeCompare(right.time))
}

function shiftDate(iso: string, days: number) {
  const [year, month, day] = iso.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}

function formatDay(iso: string) {
  const [year, month, day] = iso.split('-').map(Number)
  const date = new Date(Date.UTC(year, month - 1, day))
  const weekdays = ['воскресенье', 'понедельник', 'вторник', 'среда', 'четверг', 'пятница', 'суббота']
  const months = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря']
  return `${day} ${months[month - 1]} ${year}, ${weekdays[date.getUTCDay()]}`
}

function localToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

const scheduleDateKey = 'quiet-clinic-schedule-date'
const patientDateKey = 'quiet-clinic-patient-date'
const roleKey = 'quiet-clinic-role'

function readStoredDate(key: string) {
  try {
    const value = localStorage.getItem(key)
    return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : clinicDay
  } catch {
    return clinicDay
  }
}

function readStoredRole(): Role {
  try {
    const value = localStorage.getItem(roleKey)
    if (value === 'Менеджер') return 'Менеджер'
    return value === 'Администратор' || value === 'Регистратор' ? 'Администратор' : 'Врач'
  } catch {
    return 'Врач'
  }
}

function mergeDay(current: ScheduleItem[], date: string, incoming: ScheduleItem[]) {
  return sortSchedule([...current.filter((item) => item.date !== date), ...incoming])
}

function App() {
  const { mode, toggle } = useColorMode()
  const [page, setPage] = useState<Page>('schedule')
  const [role, setRole] = useState<Role>(readStoredRole)
  const [query, setQuery] = useState('')
  const [drawer, setDrawer] = useState(false)
  const [booking, setBooking] = useState(false)
  const [bookingKey, setBookingKey] = useState(0)
  const [bookingError, setBookingError] = useState('')
  const [patientDialog, setPatientDialog] = useState(false)
  const [editPatient, setEditPatient] = useState<Patient | null>(null)
  const [editError, setEditError] = useState('')
  const [practitioners, setPractitioners] = useState<Practitioner[]>(demoPractitioners)
  const [doctorFilter, setDoctorFilter] = useState('all')
  const [selectedPatientId, setSelectedPatientId] = useState(demoPatients[0].id)
  const [notice, setNotice] = useState('')
  const [saved, setSaved] = useState(false)
  const [signed, setSigned] = useState(false)
  const [signDialog, setSignDialog] = useState(false)
  const [diagnosis, setDiagnosis] = useState('Тревожно-депрессивная симптоматика на фоне длительного стресса.')
  const [prescription, setPrescription] = useState('Продолжить наблюдение. Контрольный визит через 2 недели.')
  const [scheduleItems, setScheduleItems] = useState(demoAppointments)
  const [patientItems, setPatientItems] = useState(demoPatients)
  const [apiOnline, setApiOnline] = useState(false)
  const [scheduleDate, setScheduleDate] = useState(() => readStoredDate(scheduleDateKey))
  const [patientDate, setPatientDate] = useState(() => readStoredDate(patientDateKey))
  const loadGeneration = useRef(0)

  useEffect(() => {
    localStorage.setItem(roleKey, role)
  }, [role])

  useEffect(() => {
    localStorage.setItem(scheduleDateKey, scheduleDate)
  }, [scheduleDate])

  useEffect(() => {
    localStorage.setItem(patientDateKey, patientDate)
  }, [patientDate])

  useEffect(() => {
    let cancelled = false
    loadPatients()
      .then((remotePatients) => {
        if (cancelled) return
        setPatientItems(remotePatients)
        setSelectedPatientId((current) => remotePatients.some((patient) => patient.id === current) ? current : remotePatients[0]?.id ?? current)
        setApiOnline(true)
      })
      .catch(() => {
        if (!cancelled) setApiOnline(false)
      })
    loadPractitioners()
      .then((remote) => { if (!cancelled) setPractitioners(remote) })
      .catch(() => { /* список врачей по умолчанию остаётся из демо-данных */ })
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    if (!apiOnline) return
    const generation = ++loadGeneration.current
    let cancelled = false
    const dates = [...new Set([scheduleDate, patientDate])]
    Promise.all(dates.map((date) => loadSchedule(date).then((items) => ({ date, items }))))
      .then((results) => {
        if (cancelled || generation !== loadGeneration.current) return
        setScheduleItems((current) => results.reduce((items, result) => mergeDay(items, result.date, result.items), current))
      })
      .catch(() => {
        if (!cancelled && generation === loadGeneration.current) setApiOnline(false)
      })
    return () => { cancelled = true }
  }, [apiOnline, scheduleDate, patientDate])

  const visiblePatients = useMemo(
    () => patientItems.filter((p) => `${p.name} ${p.phone} ${p.birth}`.toLowerCase().includes(query.toLowerCase())),
    [patientItems, query],
  )
  const selectedPatient = patientItems.find((patient) => patient.id === selectedPatientId) ?? patientItems[0]

  const navigate = (next: Page) => {
    setPage(next)
    setDrawer(false)
  }

  const openBooking = () => {
    setBookingError('')
    setBookingKey((value) => value + 1)
    setBooking(true)
  }

  const openPatient = (patient: Patient) => {
    setSelectedPatientId(patient.id)
    setPatientDate(scheduleDate)
    navigate('patient')
  }

  const cancelBooking = async (item: ScheduleItem) => {
    if (role !== 'Администратор' || item.status === 'Отменена' || !item.id) return
    const markCancelled = () => setScheduleItems((items) => items.map((row) => row.id === item.id ? { ...row, status: statusLabel('CANCELLED'), statusCode: 'CANCELLED', tone: toneFor('CANCELLED') } : row))
    if (apiOnline && isUuid(item.id)) {
      const generation = ++loadGeneration.current
      try {
        await cancelAppointment(item.id)
        const day = await loadSchedule(item.date)
        if (generation !== loadGeneration.current) return
        setScheduleItems((current) => mergeDay(current, item.date, day))
        setNotice('Запись отменена')
        setSaved(true)
      } catch (error) {
        if (generation !== loadGeneration.current) return
        if (error instanceof ApiError && error.status < 500) {
          setNotice(error.message)
          setSaved(true)
          return
        }
        setApiOnline(false)
        markCancelled()
        setNotice('Сервер недоступен: отмена видна только до обновления страницы')
        setSaved(true)
      }
      return
    }
    loadGeneration.current += 1
    markCancelled()
    setNotice('Демо-режим: отмена видна только до обновления страницы')
    setSaved(true)
  }

  const changeStatus = async (item: ScheduleItem, to: AppointmentStatus) => {
    if (!item.id || !availableActions(item, role).some((action) => action.to === to)) return
    const label = statusLabel(to)
    const markLocal = () => setScheduleItems((items) => items.map((row) => row.id === item.id ? { ...row, status: label, statusCode: to, tone: toneFor(to) } : row))
    if (apiOnline && isUuid(item.id)) {
      const generation = ++loadGeneration.current
      try {
        await changeAppointmentStatus(item.id, to, apiRoles[role])
        const day = await loadSchedule(item.date)
        if (generation !== loadGeneration.current) return
        setScheduleItems((current) => mergeDay(current, item.date, day))
        setNotice(`Статус: ${label}`)
      } catch (error) {
        if (generation !== loadGeneration.current) return
        if (error instanceof ApiError && error.status < 500) {
          setNotice(error.message)
        } else {
          setApiOnline(false)
          markLocal()
          setNotice('Сервер недоступен: статус виден только до обновления страницы')
        }
      }
      setSaved(true)
      return
    }
    loadGeneration.current += 1
    markLocal()
    setNotice(`Демо-режим: статус «${label}» виден только до обновления страницы`)
    setSaved(true)
  }

  const saveEditedPatient = async (patient: Patient, input: PatientInput) => {
    const applyLocally = (updated: Patient) => {
      setPatientItems((items) => items.map((item) => item.id === patient.id ? updated : item))
      if (updated.name !== patient.name) {
        rememberPatient(updated.id, updated.name)
        setScheduleItems((items) => items.map((item) => item.name === patient.name ? { ...item, name: updated.name } : item))
      }
    }
    if (apiOnline && isUuid(patient.id)) {
      try {
        applyLocally(await updatePatient(patient.id, input))
        setNotice('Данные пациента обновлены')
      } catch (error) {
        if (error instanceof ApiError && error.status < 500) {
          setEditError(error.message)
          return
        }
        setApiOnline(false)
        applyLocally(localPatient(patient.id, input, patient.consent))
        setNotice('Сервер недоступен: изменения видны только до обновления страницы')
      }
    } else {
      applyLocally(localPatient(patient.id, input, patient.consent))
      setNotice('Демо-режим: изменения видны только до обновления страницы')
    }
    setEditPatient(null)
    setEditError('')
    setSaved(true)
  }

  const renderSidebar = (className: string) => (
    <Box className={className}>
      <Box className="brand">
        <Box className="brand-mark"><HealthAndSafetyRounded /></Box>
        <Box><Typography className="brand-name">Тихая клиника</Typography><Typography className="brand-caption">медицинская система</Typography></Box>
      </Box>
      <Typography className="nav-label">РАБОЧЕЕ ПРОСТРАНСТВО</Typography>
      <Stack spacing={0.7}>
        <button type="button" className={`nav-item ${page === 'schedule' ? 'active' : ''}`} onClick={() => navigate('schedule')}><CalendarMonthRounded />Расписание</button>
        <button type="button" className={`nav-item ${page === 'patients' || page === 'patient' ? 'active' : ''}`} onClick={() => navigate('patients')}><PeopleAltRounded />Пациенты</button>
        {role === 'Врач' && <button type="button" className={`nav-item ${page === 'visit' ? 'active' : ''}`} onClick={() => navigate('visit')}><MedicalServicesRounded />Мои приёмы</button>}
        {role !== 'Врач' && <button type="button" className={`nav-item ${page === 'analytics' ? 'active' : ''}`} onClick={() => navigate('analytics')}><InsightsRounded />Аналитика</button>}
        {role !== 'Врач' && <button type="button" className={`nav-item ${page === 'audit' ? 'active' : ''}`} onClick={() => navigate('audit')}><HistoryRounded />Журнал аудита</button>}
      </Stack>
      <Box className="privacy-note">
        <ShieldOutlined />
        <Box><Typography sx={{ fontWeight: 700, fontSize: 13 }}>Данные разделены</Typography><Typography sx={{ fontSize: 12 }}>Доступ к клинической карте — только при участии в лечении</Typography></Box>
      </Box>
      <Box className="profile">
        <Avatar sx={{ bgcolor: 'action.selected', color: 'primary.main' }}>{profiles[role].initials}</Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}><Typography noWrap sx={{ fontWeight: 700, fontSize: 14 }}>{profiles[role].name}</Typography><Typography sx={{ color: 'text.secondary', fontSize: 12 }}>{role}</Typography></Box>
      </Box>
    </Box>
  )

  const titles: Record<Page, string> = { schedule: 'Расписание', patients: 'Пациенты', patient: 'Карточка пациента', visit: 'Приём пациента', analytics: 'Аналитика', audit: 'Журнал аудита' }
  const title = titles[page]

  return (
    <Box className="app-shell">
      <Box className="desktop-sidebar">{renderSidebar('sidebar')}</Box>
      <Drawer open={drawer} onClose={() => setDrawer(false)}>{renderSidebar('sidebar sidebar-in-drawer')}</Drawer>
      <Box component="main" className="main">
        <Box className="topbar">
          <IconButton className="mobile-menu" onClick={() => setDrawer(true)}><MenuRounded /></IconButton>
          <Box><Typography className="eyebrow">{page === 'visit' ? 'КЛИНИЧЕСКИЙ КОНТУР · ДОСТУП РАЗРЕШЁН' : page === 'analytics' || page === 'audit' ? 'УПРАВЛЕНИЕ КЛИНИКОЙ' : formatDay(page === 'patient' ? patientDate : scheduleDate).toLocaleUpperCase('ru-RU')}</Typography><Typography variant="h5" sx={{ fontWeight: 750 }}>{title}</Typography></Box>
          <Box className="role-switch">
            <Tooltip title={mode === 'light' ? 'Включить тёмную тему' : 'Включить светлую тему'}>
              <IconButton onClick={toggle} aria-label={mode === 'light' ? 'Включить тёмную тему' : 'Включить светлую тему'}>
                {mode === 'light' ? <DarkModeRounded /> : <LightModeRounded />}
              </IconButton>
            </Tooltip>
            <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>Режим просмотра</Typography>
            <FormControl size="small">
              <Select value={role} onChange={(e) => { setRole(e.target.value as Role); navigate('schedule') }} aria-label="Роль пользователя">
                <MenuItem value="Врач">Врач</MenuItem>
                <MenuItem value="Администратор">Администратор</MenuItem>
                <MenuItem value="Менеджер">Менеджер</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </Box>
        <Box className="content">
          {page === 'schedule' && <Schedule role={role} date={scheduleDate} appointments={scheduleItems} practitioners={practitioners} doctorFilter={doctorFilter} onDoctorFilter={setDoctorFilter} apiOnline={apiOnline} onDate={setScheduleDate} onBooking={openBooking} onStatus={changeStatus} onPatient={(name) => { const match = patientItems.find((patient) => patient.name === name); if (match) openPatient(match); else navigate('patient') }} onVisit={() => navigate('visit')} onCancel={cancelBooking} />}
          {page === 'patients' && <Patients query={query} setQuery={setQuery} patients={visiblePatients} onPatient={openPatient} onCreate={() => setPatientDialog(true)} />}
          {page === 'patient' && selectedPatient && <PatientCard patient={selectedPatient} date={patientDate} appointments={scheduleItems} practitioners={practitioners} role={role} onEdit={() => { setEditError(''); setEditPatient(selectedPatient) }} onDate={setPatientDate} onBack={() => navigate('patients')} onVisit={() => navigate('visit')} onCancel={cancelBooking} />}
          {page === 'analytics' && role !== 'Врач' && <Analytics apiOnline={apiOnline} role={apiRoles[role]} anchor={scheduleDate} practitioners={practitioners} scheduleItems={scheduleItems} />}
          {page === 'audit' && role !== 'Врач' && <AuditLog apiOnline={apiOnline} role={apiRoles[role]} />}
          {page === 'visit' && <Visit signed={signed} diagnosis={diagnosis} prescription={prescription} setDiagnosis={setDiagnosis} setPrescription={setPrescription} onSave={() => { setNotice('Изменения сохранены'); setSaved(true) }} onSign={() => setSignDialog(true)} />}
        </Box>
      </Box>

      <BookingDialog key={bookingKey} open={booking} patients={patientItems} practitioners={practitioners} defaultPractitionerId={doctorFilter === 'all' ? practitioners[0]?.id ?? PETROV_ID : doctorFilter} error={bookingError} initialDate={scheduleDate} onClose={() => { setBooking(false); setBookingError('') }} onSave={async (draft) => {
        const patient = patientItems.find((item) => item.id === draft.patientId)
        if (!patient) {
          setBookingError('Выберите пациента из списка')
          return
        }
        if (!draft.date) {
          setBookingError('Укажите дату приёма')
          return
        }
        if (!draft.time) {
          setBookingError('Укажите время приёма')
          return
        }
        const minutes = draft.service === 'first' ? 50 : 30
        const serviceName = draft.service === 'first' ? 'Первичный приём' : 'Повторный приём'
        const localItem: ScheduleItem = {
          id: crypto.randomUUID(),
          date: draft.date,
          time: draft.time,
          duration: `${minutes} мин`,
          name: patient.name,
          type: serviceName,
          status: statusLabel('BOOKED'),
          statusCode: 'BOOKED',
          tone: toneFor('BOOKED'),
          practitionerId: draft.practitionerId,
        }
        const showLocal = (message: string) => {
          loadGeneration.current += 1
          setScheduleDate(draft.date)
          setScheduleItems((items) => sortSchedule([...items, localItem]))
          setBooking(false)
          setBookingError('')
          setNotice(message)
          setSaved(true)
        }
        if (apiOnline && isUuid(patient.id)) {
          const generation = ++loadGeneration.current
          try {
            await createAppointment({ patientId: patient.id, patientName: patient.name, practitionerId: draft.practitionerId, date: draft.date, time: draft.time, minutes, serviceName }, apiRoles[role] === 'ADMINISTRATOR' ? 'ADMINISTRATOR' : 'DOCTOR')
            const day = await loadSchedule(draft.date)
            if (generation !== loadGeneration.current) return
            setScheduleDate(draft.date)
            setScheduleItems((current) => mergeDay(current, draft.date, day))
            setBooking(false)
            setBookingError('')
            setNotice('Запись создана')
            setSaved(true)
          } catch (error) {
            if (generation !== loadGeneration.current) return
            if (error instanceof ApiError && error.status < 500) {
              setBookingError(error.message)
              return
            }
            setApiOnline(false)
            showLocal('Сервер недоступен: запись видна только до обновления страницы')
          }
          return
        }
        showLocal('Демо-режим: запись видна только до обновления страницы')
      }} />
      <Dialog open={signDialog} onClose={() => setSignDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Подписать медицинский документ?</DialogTitle>
        <DialogContent>
          <Alert severity="warning" icon={<LockOutlined />}>После подписания эту версию нельзя изменить или удалить. Исправления оформляются отдельным дополнением.</Alert>
          <Typography sx={{ mt: 2, fontSize: 14, color: 'text.secondary' }}>Будут сохранены автор, точное время, версия шаблона и контрольный хеш документа.</Typography>
        </DialogContent>
        <DialogActions><Button onClick={() => setSignDialog(false)}>Вернуться</Button><Button variant="contained" onClick={async () => {
          if (apiOnline) {
            try {
              await signDemoVisit(diagnosis, prescription)
            } catch {
              setApiOnline(false)
            }
          }
          setSigned(true)
          setSignDialog(false)
          setNotice('Документ подписан и заблокирован')
          setSaved(true)
        }}>Подписать и заблокировать</Button></DialogActions>
      </Dialog>
      <PatientDialog open={patientDialog} onClose={() => setPatientDialog(false)} onSave={async (input) => {
        if (apiOnline) {
          try {
            const created = await createPatient(input)
            setPatientItems((items) => [created, ...items])
            setNotice('Пациент сохранён')
          } catch {
            setApiOnline(false)
            setPatientItems((items) => [localPatient(crypto.randomUUID(), input), ...items])
            setNotice('Сервер недоступен: пациент виден только до обновления страницы')
          }
        } else {
          setPatientItems((items) => [localPatient(crypto.randomUUID(), input), ...items])
          setNotice('Демо-режим: пациент виден только до обновления страницы')
        }
        setPatientDialog(false)
        setSaved(true)
      }} />
      {editPatient && <PatientDialog key={editPatient.id} open mode="edit" initial={{ fullName: editPatient.name, birthDate: editPatient.birthDate, phone: editPatient.phone }} error={editError} onClose={() => { setEditPatient(null); setEditError('') }} onSave={(input) => saveEditedPatient(editPatient, input)} />}
      <Snackbar open={saved} autoHideDuration={2500} onClose={() => setSaved(false)} message={notice} />
    </Box>
  )
}

function Schedule({ role, date, appointments, practitioners, doctorFilter, onDoctorFilter, apiOnline, onDate, onBooking, onStatus, onPatient, onVisit, onCancel }: { role: Role; date: string; appointments: ScheduleItem[]; practitioners: Practitioner[]; doctorFilter: string; onDoctorFilter: (id: string) => void; apiOnline: boolean; onDate: (date: string) => void; onBooking: () => void; onStatus: (item: ScheduleItem, to: AppointmentStatus) => void; onPatient: (name: string) => void; onVisit: () => void; onCancel: (item: ScheduleItem) => void }) {
  const [menu, setMenu] = useState<{ anchor: HTMLElement; item: ScheduleItem } | null>(null)
  const visibleDoctors = practitioners.filter((doctor) => doctorFilter === 'all' || doctor.id === doctorFilter)
  const dayItems = appointments.filter((item) => item.date === date && visibleDoctors.some((doctor) => doctor.id === item.practitionerId))
  const active = dayItems.filter((item) => item.statusCode !== 'CANCELLED' && item.statusCode !== 'NO_SHOW')
  const cancelled = dayItems.filter((item) => item.statusCode === 'CANCELLED').length
  const waiting = active.filter((item) => item.statusCode === 'BOOKED').length
  const firstActiveId = active.find((item) => item.practitionerId === PETROV_ID && item.statusCode !== 'COMPLETED')?.id
  const menuActions = menu ? availableActions(menu.item, role) : []
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
                  <Box className="appointment-info" onClick={() => onPatient(item.name)} role="button" tabIndex={0}>
                    <Typography sx={{ fontWeight: 700 }}>{item.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{item.type}</Typography>
                  </Box>
                  <Chip label={item.status} size="small" className={`status-chip ${item.tone}`} />
                  <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                    {role === 'Врач' && item.id === firstActiveId ? <Button size="small" variant="contained" onClick={onVisit}>Начать приём</Button> : <IconButton size="small" aria-label={`Открыть карточку ${item.name}`} onClick={() => onPatient(item.name)}><ChevronRightRounded /></IconButton>}
                    {availableActions(item, role).length > 0 && <IconButton size="small" title="Изменить статус" aria-label={`Изменить статус ${item.time} ${item.name}`} onClick={(event) => setMenu({ anchor: event.currentTarget, item })}><MoreHorizRounded /></IconButton>}
                    {role === 'Администратор' && ['BOOKED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS'].includes(item.statusCode) && <Button size="small" variant="outlined" aria-label={`Отменить запись ${item.time} ${item.name}`} onClick={() => onCancel(item)}>Отменить</Button>}
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

function Metric({ value, label, note, accent = false }: { value: string; label: string; note: string; accent?: boolean }) {
  return <Box className={`metric ${accent ? 'accent' : ''}`}><Typography className="metric-value">{value}</Typography><Box><Typography sx={{ fontWeight: 700 }}>{label}</Typography><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{note}</Typography></Box></Box>
}

function Patients({ query, setQuery, patients, onPatient, onCreate }: { query: string; setQuery: (v: string) => void; patients: Patient[]; onPatient: (patient: Patient) => void; onCreate: () => void }) {
  return (
    <>
      <Box className="section-intro"><Box><Typography variant="h4" sx={{ fontWeight: 750 }}>Поиск пациента</Typography><Typography sx={{ color: 'text.secondary' }}>По телефону, ФИО или дате рождения</Typography></Box><Button variant="contained" startIcon={<AddRounded />} onClick={onCreate}>Новый пациент</Button></Box>
      <TextField fullWidth value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Например, Анна Михайлова или +7 916..." slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> } }} />
      <Box className="data-boundary"><ShieldOutlined /><Box><Typography sx={{ fontWeight: 700 }}>Административное представление</Typography><Typography sx={{ fontSize: 13 }}>Здесь доступны только контакты, согласия и записи. Диагнозы и содержание приёмов не включены в этот экран.</Typography></Box></Box>
      <Box className="patient-results">
        {patients.length ? patients.map((patient) => <Box className="patient-row" key={patient.id} onClick={() => onPatient(patient)} role="button" tabIndex={0} onKeyDown={(event) => { if (event.key === 'Enter') onPatient(patient) }}>
          <Avatar>{patient.name.split(' ').map((part) => part[0]).join('')}</Avatar>
          <Box className="patient-main"><Typography sx={{ fontWeight: 750 }}>{patient.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Дата рождения: {patient.birth}</Typography></Box>
          <Box><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>Телефон</Typography><Typography sx={{ fontSize: 14 }}>{patient.phone}</Typography></Box>
          <Chip label={patient.consent} size="small" color={patient.consent === 'Действует' ? 'success' : 'warning'} variant="outlined" />
          <ChevronRightRounded color="disabled" />
        </Box>) : <Box className="empty"><PersonSearchRounded /><Typography sx={{ fontWeight: 700 }}>Пациенты не найдены</Typography><Typography sx={{ color: 'text.secondary' }}>Проверьте запрос или создайте новую карточку</Typography></Box>}
      </Box>
    </>
  )
}

function PatientCard({ patient, date, appointments, practitioners, role, onEdit, onDate, onBack, onVisit, onCancel }: { patient: Patient; date: string; appointments: ScheduleItem[]; practitioners: Practitioner[]; role: Role; onEdit: () => void; onDate: (date: string) => void; onBack: () => void; onVisit: () => void; onCancel: (item: ScheduleItem) => void }) {
  const dayItems = appointments.filter((item) => item.name === patient.name && item.date === date)
  const initials = initialsOf(patient.name)
  const doctorName = (id: string) => practitioners.find((doctor) => doctor.id === id)?.fullName ?? 'Врач'
  return (
    <>
      <Button onClick={onBack} sx={{ mb: 2 }}>← К результатам поиска</Button>
      <Box className="patient-header">
        <Avatar sx={{ width: 64, height: 64, bgcolor: 'action.selected', color: 'primary.main', fontSize: 22 }}>{initials}</Avatar>
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>{patient.name}</Typography><Typography sx={{ color: 'text.secondary' }}>{patient.birth}{patient.id.startsWith('P-') ? ` · ${patient.id}` : ''}</Typography></Box>
        <Chip icon={<CheckCircleRounded />} label={patient.consent === 'Действует' ? 'Согласие действует' : patient.consent} color={patient.consent === 'Действует' ? 'success' : 'warning'} variant="outlined" />
        {role === 'Администратор' && <Button variant="outlined" startIcon={<EditRounded />} onClick={onEdit}>Редактировать</Button>}
      </Box>
      <Box className="patient-columns">
        <Box className="panel">
          <Typography className="panel-label">АДМИНИСТРАТИВНЫЕ ДАННЫЕ</Typography>
          <Info label="Телефон" value={patient.phone} />
          <Info label="Предпочтительный канал" value="SMS · нейтральный шаблон" />
          <Divider />
          <Typography sx={{ fontWeight: 700, mt: 2 }}>Записи на {formatDay(date)}</Typography>
          <Box className="date-nav" sx={{ my: 1.5 }}>
            <IconButton size="small" aria-label="Предыдущий день пациента" onClick={() => onDate(shiftDate(date, -1))}>‹</IconButton>
            <TextField size="small" type="date" label="День пациента" value={date} onChange={(event) => { if (event.target.value) onDate(event.target.value) }} slotProps={{ inputLabel: { shrink: true } }} />
            <IconButton size="small" aria-label="Следующий день пациента" onClick={() => onDate(shiftDate(date, 1))}>›</IconButton>
          </Box>
          {dayItems.length ? dayItems.map((item) => (
            <Box className="next-appointment" key={item.id ?? `${item.time}-${item.type}`}>
              <EventNoteRounded />
              <Box sx={{ flex: 1 }}><Typography sx={{ fontWeight: 700 }}>{item.time} · {item.status}</Typography><Typography sx={{ fontSize: 13 }}>{doctorName(item.practitionerId)} · {item.type.toLowerCase()}</Typography></Box>
              {role === 'Администратор' && ['BOOKED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS'].includes(item.statusCode) && <Button size="small" variant="outlined" aria-label={`Отменить запись ${item.time} ${item.name}`} onClick={() => onCancel(item)}>Отменить</Button>}
            </Box>
          )) : <Typography sx={{ color: 'text.secondary', mt: 1 }}>На этот день записей нет</Typography>}
        </Box>
        <Box className="panel clinical-panel">
          <Box className="panel-title-row"><Typography className="panel-label">КЛИНИЧЕСКИЙ КОНТУР</Typography><LockOutlined fontSize="small" /></Box>
          {role === 'Врач' ? patient.name === 'Анна Михайлова' ? <>
            <Alert severity="success">Доступ разрешён: вы назначены на сегодняшний визит.</Alert>
            <Typography sx={{ fontWeight: 700, mt: 2 }}>История документов</Typography>
            <Box className="history-item"><Box><Typography sx={{ fontWeight: 650 }}>Первичный осмотр</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>20 октября 2026 · черновик</Typography></Box><Button size="small" onClick={onVisit}>Открыть</Button></Box>
            <Box className="history-item"><Box><Typography sx={{ fontWeight: 650 }}>Консультация</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>18 сентября 2026 · подписано</Typography></Box><Chip size="small" icon={<LockOutlined />} label="Заблокирован" /></Box>
          </> : <Box className="locked-state"><EventNoteRounded /><Typography sx={{ fontWeight: 750 }}>Документов приёма пока нет</Typography><Typography sx={{ fontSize: 14 }}>Клинические записи появятся после визита, в котором вы участвуете.</Typography></Box> : <>
            <Box className="locked-state"><LockOutlined /><Typography sx={{ fontWeight: 750 }}>Клинические данные недоступны</Typography><Typography sx={{ fontSize: 14 }}>Роль «Администратор» не имеет доступа к жалобам, диагнозам, назначениям и медицинским документам.</Typography></Box>
          </>}
        </Box>
      </Box>
    </>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return <Box className="info-row"><Typography sx={{ color: 'text.secondary', fontSize: 13 }}>{label}</Typography><Typography sx={{ fontWeight: 600 }}>{value}</Typography></Box>
}

function Visit({ signed, diagnosis, prescription, setDiagnosis, setPrescription, onSave, onSign }: { signed: boolean; diagnosis: string; prescription: string; setDiagnosis: (v: string) => void; setPrescription: (v: string) => void; onSave: () => void; onSign: () => void }) {
  return (
    <>
      <Box className="visit-banner">
        <Box><Typography className="eyebrow">ПЕРВИЧНЫЙ ПРИЁМ · 09:00–09:50</Typography><Typography variant="h4" sx={{ fontWeight: 750 }}>Анна Михайлова</Typography><Typography sx={{ color: 'text.secondary' }}>35 лет · карта P-10482</Typography></Box>
        <Chip icon={signed ? <LockOutlined /> : <EditIcon />} label={signed ? 'Подписан · заблокирован' : 'Черновик · версия 4'} color={signed ? 'success' : 'warning'} variant="outlined" />
      </Box>
      {signed && <Alert severity="success" icon={<LockOutlined />} sx={{ mb: 2 }}>Документ подписан 20.10.2026 в 10:06 врачом А. Петровым. Изменение исходной версии недоступно.</Alert>}
      <Box className="visit-layout">
        <Box className="clinical-form panel">
          <FormSection number="01" title="Жалобы и анамнез">
            <TextField multiline minRows={3} fullWidth defaultValue="Повышенная тревожность, нарушения сна, утомляемость в течение последних трёх месяцев." disabled={signed} />
          </FormSection>
          <FormSection number="02" title="Психический статус">
            <TextField multiline minRows={3} fullWidth defaultValue="Ориентирована верно. Контакт продуктивный. Настроение снижено, тревожный фон. Суицидальные мысли отрицает." disabled={signed} />
          </FormSection>
          <FormSection number="03" title="Диагноз">
            <TextField multiline minRows={2} fullWidth value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)} helperText="Свободный текст — кодирование по МКБ в MVP не требуется" disabled={signed} />
          </FormSection>
          <FormSection number="04" title="Назначения и рекомендации">
            <TextField multiline minRows={3} fullWidth value={prescription} onChange={(e) => setPrescription(e.target.value)} disabled={signed} />
          </FormSection>
        </Box>
        <Box>
          <Box className="panel sticky-summary">
            <Typography sx={{ fontWeight: 750 }}>Документ приёма</Typography>
            <Info label="Шаблон" value="Первичный осмотр · v2.4" />
            <Info label="Автор" value="Александр Петров" />
            <Info label="Последнее сохранение" value="10:02" />
            <Divider />
            <Typography sx={{ fontSize: 12, color: 'text.secondary', mt: 2 }}>Подписание зафиксирует ровно показанную версию и контрольный хеш.</Typography>
            {!signed ? <Stack spacing={1.2} sx={{ mt: 2 }}><Button variant="outlined" onClick={onSave}>Сохранить черновик</Button><Button variant="contained" startIcon={<LockOutlined />} onClick={onSign}>Проверить и подписать</Button></Stack> : <Button fullWidth variant="outlined" sx={{ mt: 2 }}>Создать дополнение</Button>}
          </Box>
        </Box>
      </Box>
    </>
  )
}

function EditIcon() { return <Box component="span" sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: 'currentColor' }} /> }
function FormSection({ number, title, children }: { number: string; title: string; children: React.ReactNode }) {
  return <Box className="form-section"><Box className="form-section-title"><Typography>{number}</Typography><Typography sx={{ fontWeight: 750 }}>{title}</Typography></Box>{children}</Box>
}

function PatientDialog({ open, mode = 'create', initial, error = '', onClose, onSave }: { open: boolean; mode?: 'create' | 'edit'; initial?: PatientInput; error?: string; onClose: () => void; onSave: (input: PatientInput) => void }) {
  const [fullName, setFullName] = useState(initial?.fullName ?? 'Ирина Белова')
  const [birthDate, setBirthDate] = useState(initial?.birthDate ?? '1994-03-12')
  const [phone, setPhone] = useState(initial?.phone ?? '+7 903 111-22-33')
  const edit = mode === 'edit'
  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle>{edit ? 'Данные пациента' : 'Новый пациент'}</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="ФИО" value={fullName} onChange={(event) => setFullName(event.target.value)} />
        <TextField label="Дата рождения" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label="Телефон" value={phone} onChange={(event) => setPhone(event.target.value)} />
        {edit && <Alert severity="info">В журнал аудита попадут только названия изменённых полей, без самих значений.</Alert>}
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={!fullName.trim() || !birthDate || !phone.trim()} onClick={() => onSave({ fullName: fullName.trim(), birthDate, phone: phone.trim() })}>{edit ? 'Сохранить изменения' : 'Сохранить пациента'}</Button>
    </DialogActions>
  </Dialog>
}

function BookingDialog({ open, patients, practitioners, defaultPractitionerId, error, initialDate, onClose, onSave }: { open: boolean; patients: Patient[]; practitioners: Practitioner[]; defaultPractitionerId: string; error: string; initialDate: string; onClose: () => void; onSave: (draft: BookingDraft) => void }) {
  const fallbackPatientId = patients.find((item) => item.name === 'Анна Михайлова')?.id ?? patients[0]?.id ?? ''
  const [patientId, setPatientId] = useState(fallbackPatientId)
  const [date, setDate] = useState(initialDate)
  const [time, setTime] = useState('13:00')
  const [practitionerId, setPractitionerId] = useState(defaultPractitionerId)
  const [service, setService] = useState<'repeat' | 'first'>('repeat')
  const selectedPatientId = patients.some((item) => item.id === patientId) ? patientId : fallbackPatientId
  const selectedPractitionerId = practitioners.some((item) => item.id === practitionerId) ? practitionerId : practitioners[0]?.id ?? ''

  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>Новая запись <IconButton onClick={onClose}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField select label="Пациент" value={selectedPatientId} onChange={(event) => setPatientId(event.target.value)}>
          {patients.map((patient) => <MenuItem key={patient.id} value={patient.id}>{patient.name}</MenuItem>)}
        </TextField>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField label="Дата" type="date" value={date} onChange={(event) => setDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth /><TextField label="Время" type="time" value={time} onChange={(event) => setTime(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth /></Stack>
        <TextField select label="Врач" value={selectedPractitionerId} onChange={(event) => setPractitionerId(event.target.value)}>
          {practitioners.map((doctor) => <MenuItem key={doctor.id} value={doctor.id}>{doctor.fullName} · {doctor.specialty.toLowerCase()}</MenuItem>)}
        </TextField>
        <TextField select label="Услуга" value={service} onChange={(event) => setService(event.target.value as 'repeat' | 'first')}><MenuItem value="repeat">Повторный приём · 30 мин</MenuItem><MenuItem value="first">Первичный приём · 50 мин</MenuItem></TextField>
        <Alert severity="info">Пациенту ничего не отправляется. Канал сообщений пока не подключён.</Alert>
      </Stack>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" disabled={!selectedPatientId || !selectedPractitionerId || !date || !time} onClick={() => onSave({ patientId: selectedPatientId, date, time, service, practitionerId: selectedPractitionerId })}>Создать запись</Button></DialogActions>
  </Dialog>
}

export default App
