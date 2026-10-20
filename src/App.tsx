import { useEffect, useMemo, useState } from 'react'
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
  LightModeRounded,
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
  createAppointment,
  createPatient,
  loadPatients,
  loadSchedule,
  signDemoVisit,
  type PatientItem,
  type ScheduleItem,
} from './api'
import { useColorMode } from './theme'

type Page = 'schedule' | 'patients' | 'patient' | 'visit'
type Role = 'Регистратор' | 'Врач'
type Patient = PatientItem

const demoAppointments: ScheduleItem[] = [
  { time: '09:00', duration: '50 мин', name: 'Анна Михайлова', type: 'Первичный приём', status: 'Прибыл', tone: 'green' },
  { time: '10:10', duration: '30 мин', name: 'Дмитрий Волков', type: 'Повторный приём', status: 'Подтверждён', tone: 'blue' },
  { time: '11:00', duration: '50 мин', name: 'Мария Соколова', type: 'Первичный приём', status: 'Ожидает', tone: 'orange' },
  { time: '12:00', duration: '30 мин', name: 'Алексей Морозов', type: 'Повторный приём', status: 'Подтверждён', tone: 'blue' },
]

const demoPatients: Patient[] = [
  { name: 'Анна Михайлова', birth: '14.05.1991', phone: '+7 916 245-18-72', id: 'P-10482', consent: 'Действует' },
  { name: 'Дмитрий Волков', birth: '02.11.1985', phone: '+7 903 782-44-16', id: 'P-10396', consent: 'Действует' },
  { name: 'Мария Соколова', birth: '28.02.1996', phone: '+7 925 663-02-41', id: 'P-10251', consent: 'Требует обновления' },
]

const scheduleDate = '2026-10-20'

type BookingDraft = {
  patientId: string
  date: string
  time: string
  service: 'repeat' | 'first'
  doctor: 'petrov' | 'lebedeva'
}

function isUuid(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
}

function sortSchedule(items: ScheduleItem[]) {
  return [...items].sort((left, right) => left.time.localeCompare(right.time))
}

function App() {
  const { mode, toggle } = useColorMode()
  const [page, setPage] = useState<Page>('schedule')
  const [role, setRole] = useState<Role>('Врач')
  const [query, setQuery] = useState('')
  const [drawer, setDrawer] = useState(false)
  const [booking, setBooking] = useState(false)
  const [bookingKey, setBookingKey] = useState(0)
  const [bookingError, setBookingError] = useState('')
  const [patientDialog, setPatientDialog] = useState(false)
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

  useEffect(() => {
    let cancelled = false
    loadPatients()
      .then(async (remotePatients) => {
        const schedule = await loadSchedule()
        if (cancelled) return
        setPatientItems(remotePatients)
        setScheduleItems(schedule)
        setSelectedPatientId((current) => remotePatients.some((patient) => patient.id === current) ? current : remotePatients[0]?.id ?? current)
        setApiOnline(true)
      })
      .catch(() => {
        if (!cancelled) setApiOnline(false)
      })
    return () => { cancelled = true }
  }, [])

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
    navigate('patient')
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
      </Stack>
      <Box className="privacy-note">
        <ShieldOutlined />
        <Box><Typography sx={{ fontWeight: 700, fontSize: 13 }}>Данные разделены</Typography><Typography sx={{ fontSize: 12 }}>Доступ к клинической карте — только при участии в лечении</Typography></Box>
      </Box>
      <Box className="profile">
        <Avatar sx={{ bgcolor: 'action.selected', color: 'primary.main' }}>ЕС</Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}><Typography noWrap sx={{ fontWeight: 700, fontSize: 14 }}>Елена Смирнова</Typography><Typography sx={{ color: 'text.secondary', fontSize: 12 }}>{role}</Typography></Box>
      </Box>
    </Box>
  )

  const title = page === 'schedule' ? 'Расписание' : page === 'patients' ? 'Пациенты' : page === 'patient' ? 'Карточка пациента' : 'Приём пациента'

  return (
    <Box className="app-shell">
      <Box className="desktop-sidebar">{renderSidebar('sidebar')}</Box>
      <Drawer open={drawer} onClose={() => setDrawer(false)}>{renderSidebar('sidebar sidebar-in-drawer')}</Drawer>
      <Box component="main" className="main">
        <Box className="topbar">
          <IconButton className="mobile-menu" onClick={() => setDrawer(true)}><MenuRounded /></IconButton>
          <Box><Typography className="eyebrow">{page === 'visit' ? 'КЛИНИЧЕСКИЙ КОНТУР · ДОСТУП РАЗРЕШЁН' : 'ВТОРНИК, 20 ОКТЯБРЯ'}</Typography><Typography variant="h5" sx={{ fontWeight: 750 }}>{title}</Typography></Box>
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
                <MenuItem value="Регистратор">Регистратор</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </Box>
        <Box className="content">
          {page === 'schedule' && <Schedule role={role} appointments={scheduleItems} apiOnline={apiOnline} onBooking={openBooking} onPatient={(name) => { const match = patientItems.find((patient) => patient.name === name); if (match) openPatient(match); else navigate('patient') }} onVisit={() => navigate('visit')} />}
          {page === 'patients' && <Patients query={query} setQuery={setQuery} patients={visiblePatients} onPatient={openPatient} onCreate={() => setPatientDialog(true)} />}
          {page === 'patient' && selectedPatient && <PatientCard patient={selectedPatient} appointments={scheduleItems} role={role} onBack={() => navigate('patients')} onVisit={() => navigate('visit')} />}
          {page === 'visit' && <Visit signed={signed} diagnosis={diagnosis} prescription={prescription} setDiagnosis={setDiagnosis} setPrescription={setPrescription} onSave={() => { setNotice('Изменения сохранены'); setSaved(true) }} onSign={() => setSignDialog(true)} />}
        </Box>
      </Box>

      <BookingDialog key={bookingKey} open={booking} patients={patientItems} error={bookingError} onClose={() => { setBooking(false); setBookingError('') }} onSave={async (draft) => {
        const patient = patientItems.find((item) => item.id === draft.patientId)
        if (!patient) {
          setBookingError('Выберите пациента из списка')
          return
        }
        if (draft.date !== scheduleDate) {
          setBookingError('На экране открыто 20 октября. Выберите эту дату, чтобы запись появилась в расписании.')
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
          time: draft.time,
          duration: `${minutes} мин`,
          name: patient.name,
          type: serviceName,
          status: 'Ожидает',
          tone: 'blue',
        }
        const showLocal = (message: string) => {
          setScheduleItems((items) => sortSchedule([...items, localItem]))
          setBooking(false)
          setBookingError('')
          setNotice(message)
          setSaved(true)
        }
        if (apiOnline && draft.doctor !== 'petrov') {
          setBookingError('В расписании сейчас доступен только Александр Петров')
          return
        }
        if (apiOnline && isUuid(patient.id)) {
          try {
            await createAppointment({ patientId: patient.id, patientName: patient.name, date: draft.date, time: draft.time, minutes, serviceName })
            setScheduleItems(sortSchedule(await loadSchedule()))
            setBooking(false)
            setBookingError('')
            setNotice('Запись создана')
            setSaved(true)
          } catch (error) {
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
            setPatientItems((items) => [{ id: crypto.randomUUID(), name: input.fullName, birth: input.birthDate.split('-').reverse().join('.'), phone: input.phone, consent: 'Действует' }, ...items])
            setNotice('Сервер недоступен: пациент виден только до обновления страницы')
          }
        } else {
          setPatientItems((items) => [{ id: crypto.randomUUID(), name: input.fullName, birth: input.birthDate.split('-').reverse().join('.'), phone: input.phone, consent: 'Действует' }, ...items])
          setNotice('Демо-режим: пациент виден только до обновления страницы')
        }
        setPatientDialog(false)
        setSaved(true)
      }} />
      <Snackbar open={saved} autoHideDuration={2500} onClose={() => setSaved(false)} message={notice} />
    </Box>
  )
}

function Schedule({ role, appointments, apiOnline, onBooking, onPatient, onVisit }: { role: Role; appointments: ScheduleItem[]; apiOnline: boolean; onBooking: () => void; onPatient: (name: string) => void; onVisit: () => void }) {
  return (
    <>
      <Box className="summary-grid">
        <Metric value="8" label="приёмов сегодня" note="5 подтверждено" />
        <Metric value="2" label="свободных окна" note="80 минут" />
        <Metric value="1" label="ожидает оформления" note="приём завершён" accent />
      </Box>
      <Box className="toolbar">
        <Box className="date-nav"><IconButton size="small">‹</IconButton><Typography sx={{ fontWeight: 700 }}>20 октября, вторник</Typography><IconButton size="small">›</IconButton></Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><Chip size="small" label={apiOnline ? 'API подключён' : 'Демо-режим'} color={apiOnline ? 'success' : 'default'} variant="outlined" /><Button variant="outlined" startIcon={<ScheduleRounded />}>Сегодня</Button><Button variant="contained" startIcon={<AddRounded />} onClick={onBooking}>Новая запись</Button></Stack>
      </Box>
      <Box className="schedule-card">
        <Box className="doctor-heading">
          <Avatar sx={{ bgcolor: '#244d48' }}>АП</Avatar>
          <Box><Typography sx={{ fontWeight: 750 }}>Александр Петров</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Психиатр · кабинет 204</Typography></Box>
          <Chip label="09:00–18:00" size="small" variant="outlined" />
        </Box>
        <Box className="appointment-list">
          {appointments.map((item, index) => (
            <Box className="appointment" key={item.id ?? `${item.time}-${item.name}`}>
              <Box className="time"><Typography sx={{ fontWeight: 750 }}>{item.time}</Typography><Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{item.duration}</Typography></Box>
              <Box className={`status-line ${item.tone}`} />
              <Box className="appointment-info" onClick={() => onPatient(item.name)} role="button" tabIndex={0}>
                <Typography sx={{ fontWeight: 700 }}>{item.name}</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{item.type}</Typography>
              </Box>
              <Chip label={item.status} size="small" className={`status-chip ${item.tone}`} />
              {role === 'Врач' && index === 0 ? <Button size="small" variant="contained" onClick={onVisit}>Начать приём</Button> : <IconButton size="small" aria-label={`Открыть карточку ${item.name}`} onClick={() => onPatient(item.name)}><ChevronRightRounded /></IconButton>}
            </Box>
          ))}
          <Box className="free-slot"><Typography sx={{ color: 'text.secondary' }}>13:00</Typography><Divider /><Button size="small" onClick={onBooking}>+ Записать на свободное время</Button></Box>
        </Box>
      </Box>
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

function PatientCard({ patient, appointments, role, onBack, onVisit }: { patient: Patient; appointments: ScheduleItem[]; role: Role; onBack: () => void; onVisit: () => void }) {
  const next = appointments.find((item) => item.name === patient.name)
  const initials = patient.name.split(' ').map((part) => part[0]).join('').slice(0, 2)
  return (
    <>
      <Button onClick={onBack} sx={{ mb: 2 }}>← К результатам поиска</Button>
      <Box className="patient-header">
        <Avatar sx={{ width: 64, height: 64, bgcolor: 'action.selected', color: 'primary.main', fontSize: 22 }}>{initials}</Avatar>
        <Box><Typography variant="h4" sx={{ fontWeight: 750 }}>{patient.name}</Typography><Typography sx={{ color: 'text.secondary' }}>{patient.birth}{patient.id.startsWith('P-') ? ` · ${patient.id}` : ''}</Typography></Box>
        <Chip icon={<CheckCircleRounded />} label={patient.consent === 'Действует' ? 'Согласие действует' : patient.consent} color={patient.consent === 'Действует' ? 'success' : 'warning'} variant="outlined" />
      </Box>
      <Box className="patient-columns">
        <Box className="panel">
          <Typography className="panel-label">АДМИНИСТРАТИВНЫЕ ДАННЫЕ</Typography>
          <Info label="Телефон" value={patient.phone} />
          <Info label="Предпочтительный канал" value="SMS · нейтральный шаблон" />
          <Divider /><Typography sx={{ fontWeight: 700, mt: 2 }}>Ближайшая запись</Typography>
          {next ? <Box className="next-appointment"><EventNoteRounded /><Box><Typography sx={{ fontWeight: 700 }}>Сегодня, {next.time}</Typography><Typography sx={{ fontSize: 13 }}>Александр Петров · {next.type.toLowerCase()}</Typography></Box></Box> : <Typography sx={{ color: 'text.secondary', mt: 1 }}>На сегодня записей нет</Typography>}
        </Box>
        <Box className="panel clinical-panel">
          <Box className="panel-title-row"><Typography className="panel-label">КЛИНИЧЕСКИЙ КОНТУР</Typography><LockOutlined fontSize="small" /></Box>
          {role === 'Врач' ? patient.name === 'Анна Михайлова' ? <>
            <Alert severity="success">Доступ разрешён: вы назначены на сегодняшний визит.</Alert>
            <Typography sx={{ fontWeight: 700, mt: 2 }}>История документов</Typography>
            <Box className="history-item"><Box><Typography sx={{ fontWeight: 650 }}>Первичный осмотр</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>20 октября 2026 · черновик</Typography></Box><Button size="small" onClick={onVisit}>Открыть</Button></Box>
            <Box className="history-item"><Box><Typography sx={{ fontWeight: 650 }}>Консультация</Typography><Typography sx={{ fontSize: 13, color: 'text.secondary' }}>18 сентября 2026 · подписано</Typography></Box><Chip size="small" icon={<LockOutlined />} label="Заблокирован" /></Box>
          </> : <Box className="locked-state"><EventNoteRounded /><Typography sx={{ fontWeight: 750 }}>Документов приёма пока нет</Typography><Typography sx={{ fontSize: 14 }}>Клинические записи появятся после визита, в котором вы участвуете.</Typography></Box> : <>
            <Box className="locked-state"><LockOutlined /><Typography sx={{ fontWeight: 750 }}>Клинические данные недоступны</Typography><Typography sx={{ fontSize: 14 }}>Роль «Регистратор» не имеет доступа к жалобам, диагнозам, назначениям и медицинским документам.</Typography></Box>
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

function PatientDialog({ open, onClose, onSave }: { open: boolean; onClose: () => void; onSave: (input: { fullName: string; birthDate: string; phone: string }) => void }) {
  const [fullName, setFullName] = useState('Ирина Белова')
  const [birthDate, setBirthDate] = useState('1994-03-12')
  const [phone, setPhone] = useState('+7 903 111-22-33')
  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle>Новый пациент</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        <TextField label="ФИО" value={fullName} onChange={(event) => setFullName(event.target.value)} />
        <TextField label="Дата рождения" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label="Телефон" value={phone} onChange={(event) => setPhone(event.target.value)} />
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={!fullName.trim() || !birthDate || !phone.trim()} onClick={() => onSave({ fullName: fullName.trim(), birthDate, phone: phone.trim() })}>Сохранить пациента</Button>
    </DialogActions>
  </Dialog>
}

function BookingDialog({ open, patients, error, onClose, onSave }: { open: boolean; patients: Patient[]; error: string; onClose: () => void; onSave: (draft: BookingDraft) => void }) {
  const fallbackPatientId = patients.find((item) => item.name === 'Анна Михайлова')?.id ?? patients[0]?.id ?? ''
  const [patientId, setPatientId] = useState(fallbackPatientId)
  const [date, setDate] = useState(scheduleDate)
  const [time, setTime] = useState('13:00')
  const [doctor, setDoctor] = useState<'petrov' | 'lebedeva'>('petrov')
  const [service, setService] = useState<'repeat' | 'first'>('repeat')
  const selectedPatientId = patients.some((item) => item.id === patientId) ? patientId : fallbackPatientId

  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>Новая запись <IconButton onClick={onClose}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField select label="Пациент" value={selectedPatientId} onChange={(event) => setPatientId(event.target.value)}>
          {patients.map((patient) => <MenuItem key={patient.id} value={patient.id}>{patient.name}</MenuItem>)}
        </TextField>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}><TextField label="Дата" type="date" value={date} onChange={(event) => setDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth /><TextField label="Время" type="time" value={time} onChange={(event) => setTime(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} fullWidth /></Stack>
        <TextField select label="Врач" value={doctor} onChange={(event) => setDoctor(event.target.value as 'petrov' | 'lebedeva')}><MenuItem value="petrov">Александр Петров</MenuItem><MenuItem value="lebedeva">Ольга Лебедева</MenuItem></TextField>
        <TextField select label="Услуга" value={service} onChange={(event) => setService(event.target.value as 'repeat' | 'first')}><MenuItem value="repeat">Повторный приём · 30 мин</MenuItem><MenuItem value="first">Первичный приём · 50 мин</MenuItem></TextField>
        <Alert severity="info">Пациенту будет отправлено нейтральное сообщение без указания профиля клиники и цели визита.</Alert>
      </Stack>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" disabled={!selectedPatientId || !date || !time} onClick={() => onSave({ patientId: selectedPatientId, date, time, service, doctor })}>Создать запись</Button></DialogActions>
  </Dialog>
}

export default App
