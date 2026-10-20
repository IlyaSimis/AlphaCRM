import { useMemo, useState } from 'react'
import {
  AddRounded,
  CalendarMonthRounded,
  CheckCircleRounded,
  ChevronRightRounded,
  CloseRounded,
  EventNoteRounded,
  HealthAndSafetyRounded,
  LockOutlined,
  MenuRounded,
  PeopleAltRounded,
  PersonSearchRounded,
  ScheduleRounded,
  SearchRounded,
  ShieldOutlined,
  Stethoscope,
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

type Page = 'schedule' | 'patients' | 'patient' | 'visit'
type Role = 'Регистратор' | 'Врач'
type Patient = { name: string; birth: string; phone: string; id: string; consent: string }

const appointments = [
  { time: '09:00', duration: '50 мин', name: 'Анна Михайлова', type: 'Первичный приём', status: 'Прибыл', tone: 'green' },
  { time: '10:10', duration: '30 мин', name: 'Дмитрий Волков', type: 'Повторный приём', status: 'Подтверждён', tone: 'blue' },
  { time: '11:00', duration: '50 мин', name: 'Мария Соколова', type: 'Первичный приём', status: 'Ожидает', tone: 'orange' },
  { time: '12:00', duration: '30 мин', name: 'Алексей Морозов', type: 'Повторный приём', status: 'Подтверждён', tone: 'blue' },
]

const patients = [
  { name: 'Анна Михайлова', birth: '14.05.1991', phone: '+7 916 245-18-72', id: 'P-10482', consent: 'Действует' },
  { name: 'Дмитрий Волков', birth: '02.11.1985', phone: '+7 903 782-44-16', id: 'P-10396', consent: 'Действует' },
  { name: 'Мария Соколова', birth: '28.02.1996', phone: '+7 925 663-02-41', id: 'P-10251', consent: 'Требует обновления' },
]

function App() {
  const [page, setPage] = useState<Page>('schedule')
  const [role, setRole] = useState<Role>('Врач')
  const [query, setQuery] = useState('')
  const [drawer, setDrawer] = useState(false)
  const [booking, setBooking] = useState(false)
  const [saved, setSaved] = useState(false)
  const [signed, setSigned] = useState(false)
  const [signDialog, setSignDialog] = useState(false)
  const [diagnosis, setDiagnosis] = useState('Тревожно-депрессивная симптоматика на фоне длительного стресса.')
  const [prescription, setPrescription] = useState('Продолжить наблюдение. Контрольный визит через 2 недели.')

  const visiblePatients = useMemo(
    () => patients.filter((p) => `${p.name} ${p.phone} ${p.birth}`.toLowerCase().includes(query.toLowerCase())),
    [query],
  )

  const navigate = (next: Page) => {
    setPage(next)
    setDrawer(false)
  }

  const sidebar = (
    <Box className="sidebar">
      <Box className="brand">
        <Box className="brand-mark"><HealthAndSafetyRounded /></Box>
        <Box><Typography className="brand-name">Тихая клиника</Typography><Typography className="brand-caption">медицинская система</Typography></Box>
      </Box>
      <Typography className="nav-label">РАБОЧЕЕ ПРОСТРАНСТВО</Typography>
      <Stack gap={0.7}>
        <button className={`nav-item ${page === 'schedule' ? 'active' : ''}`} onClick={() => navigate('schedule')}><CalendarMonthRounded />Расписание</button>
        <button className={`nav-item ${page === 'patients' || page === 'patient' ? 'active' : ''}`} onClick={() => navigate('patients')}><PeopleAltRounded />Пациенты</button>
        {role === 'Врач' && <button className={`nav-item ${page === 'visit' ? 'active' : ''}`} onClick={() => navigate('visit')}><Stethoscope />Мои приёмы</button>}
      </Stack>
      <Box className="privacy-note">
        <ShieldOutlined />
        <Box><Typography fontWeight={700} fontSize={13}>Данные разделены</Typography><Typography fontSize={12}>Доступ к клинической карте — только при участии в лечении</Typography></Box>
      </Box>
      <Box className="profile">
        <Avatar sx={{ bgcolor: '#d9eee8', color: '#176b5b' }}>ЕС</Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}><Typography fontWeight={700} fontSize={14} noWrap>Елена Смирнова</Typography><Typography color="text.secondary" fontSize={12}>{role}</Typography></Box>
      </Box>
    </Box>
  )

  const title = page === 'schedule' ? 'Расписание' : page === 'patients' ? 'Пациенты' : page === 'patient' ? 'Карточка пациента' : 'Приём пациента'

  return (
    <Box className="app-shell">
      <Box className="desktop-sidebar">{sidebar}</Box>
      <Drawer open={drawer} onClose={() => setDrawer(false)}>{sidebar}</Drawer>
      <Box component="main" className="main">
        <Box className="topbar">
          <IconButton className="mobile-menu" onClick={() => setDrawer(true)}><MenuRounded /></IconButton>
          <Box><Typography className="eyebrow">{page === 'visit' ? 'КЛИНИЧЕСКИЙ КОНТУР · ДОСТУП РАЗРЕШЁН' : 'ВТОРНИК, 20 ОКТЯБРЯ'}</Typography><Typography variant="h5" fontWeight={750}>{title}</Typography></Box>
          <Box className="role-switch">
            <Typography fontSize={12} color="text.secondary">Режим просмотра</Typography>
            <FormControl size="small">
              <Select value={role} onChange={(e) => { setRole(e.target.value as Role); navigate('schedule') }} aria-label="Роль пользователя">
                <MenuItem value="Врач">Врач</MenuItem>
                <MenuItem value="Регистратор">Регистратор</MenuItem>
              </Select>
            </FormControl>
          </Box>
        </Box>
        <Box className="content">
          {page === 'schedule' && <Schedule role={role} onBooking={() => setBooking(true)} onPatient={() => navigate('patient')} onVisit={() => navigate('visit')} />}
          {page === 'patients' && <Patients query={query} setQuery={setQuery} patients={visiblePatients} onPatient={() => navigate('patient')} />}
          {page === 'patient' && <PatientCard role={role} onBack={() => navigate('patients')} onVisit={() => navigate('visit')} />}
          {page === 'visit' && <Visit signed={signed} diagnosis={diagnosis} prescription={prescription} setDiagnosis={setDiagnosis} setPrescription={setPrescription} onSave={() => setSaved(true)} onSign={() => setSignDialog(true)} />}
        </Box>
      </Box>

      <BookingDialog open={booking} onClose={() => setBooking(false)} onSave={() => { setBooking(false); setSaved(true) }} />
      <Dialog open={signDialog} onClose={() => setSignDialog(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Подписать медицинский документ?</DialogTitle>
        <DialogContent>
          <Alert severity="warning" icon={<LockOutlined />}>После подписания эту версию нельзя изменить или удалить. Исправления оформляются отдельным дополнением.</Alert>
          <Typography mt={2} fontSize={14} color="text.secondary">Будут сохранены автор, точное время, версия шаблона и контрольный хеш документа.</Typography>
        </DialogContent>
        <DialogActions><Button onClick={() => setSignDialog(false)}>Вернуться</Button><Button variant="contained" onClick={() => { setSigned(true); setSignDialog(false); setSaved(true) }}>Подписать и заблокировать</Button></DialogActions>
      </Dialog>
      <Snackbar open={saved} autoHideDuration={2500} onClose={() => setSaved(false)} message={signed ? 'Документ подписан и заблокирован' : 'Изменения сохранены'} />
    </Box>
  )
}

function Schedule({ role, onBooking, onPatient, onVisit }: { role: Role; onBooking: () => void; onPatient: () => void; onVisit: () => void }) {
  return (
    <>
      <Box className="summary-grid">
        <Metric value="8" label="приёмов сегодня" note="5 подтверждено" />
        <Metric value="2" label="свободных окна" note="80 минут" />
        <Metric value="1" label="ожидает оформления" note="приём завершён" accent />
      </Box>
      <Box className="toolbar">
        <Box className="date-nav"><IconButton size="small">‹</IconButton><Typography fontWeight={700}>20 октября, вторник</Typography><IconButton size="small">›</IconButton></Box>
        <Stack direction="row" gap={1}><Button variant="outlined" startIcon={<ScheduleRounded />}>Сегодня</Button><Button variant="contained" startIcon={<AddRounded />} onClick={onBooking}>Новая запись</Button></Stack>
      </Box>
      <Box className="schedule-card">
        <Box className="doctor-heading">
          <Avatar sx={{ bgcolor: '#244d48' }}>АП</Avatar>
          <Box><Typography fontWeight={750}>Александр Петров</Typography><Typography fontSize={13} color="text.secondary">Психиатр · кабинет 204</Typography></Box>
          <Chip label="09:00–18:00" size="small" variant="outlined" />
        </Box>
        <Box className="appointment-list">
          {appointments.map((item, index) => (
            <Box className="appointment" key={item.time}>
              <Box className="time"><Typography fontWeight={750}>{item.time}</Typography><Typography fontSize={12} color="text.secondary">{item.duration}</Typography></Box>
              <Box className={`status-line ${item.tone}`} />
              <Box className="appointment-info" onClick={onPatient} role="button" tabIndex={0}>
                <Typography fontWeight={700}>{item.name}</Typography><Typography fontSize={13} color="text.secondary">{item.type}</Typography>
              </Box>
              <Chip label={item.status} size="small" className={`status-chip ${item.tone}`} />
              {role === 'Врач' && index === 0 ? <Button size="small" variant="contained" onClick={onVisit}>Начать приём</Button> : <IconButton size="small" onClick={onPatient}><ChevronRightRounded /></IconButton>}
            </Box>
          ))}
          <Box className="free-slot"><Typography color="text.secondary">13:00</Typography><Divider /><Button size="small" onClick={onBooking}>+ Записать на свободное время</Button></Box>
        </Box>
      </Box>
    </>
  )
}

function Metric({ value, label, note, accent = false }: { value: string; label: string; note: string; accent?: boolean }) {
  return <Box className={`metric ${accent ? 'accent' : ''}`}><Typography className="metric-value">{value}</Typography><Box><Typography fontWeight={700}>{label}</Typography><Typography fontSize={12} color="text.secondary">{note}</Typography></Box></Box>
}

function Patients({ query, setQuery, patients, onPatient }: { query: string; setQuery: (v: string) => void; patients: Patient[]; onPatient: () => void }) {
  return (
    <>
      <Box className="section-intro"><Box><Typography variant="h4" fontWeight={750}>Поиск пациента</Typography><Typography color="text.secondary">По телефону, ФИО или дате рождения</Typography></Box><Button variant="contained" startIcon={<AddRounded />}>Новый пациент</Button></Box>
      <TextField fullWidth value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Например, Анна Михайлова или +7 916..." InputProps={{ startAdornment: <InputAdornment position="start"><SearchRounded /></InputAdornment> }} />
      <Box className="data-boundary"><ShieldOutlined /><Box><Typography fontWeight={700}>Административное представление</Typography><Typography fontSize={13}>Здесь доступны только контакты, согласия и записи. Диагнозы и содержание приёмов не включены в этот экран.</Typography></Box></Box>
      <Box className="patient-results">
        {patients.length ? patients.map((patient) => <Box className="patient-row" key={patient.id} onClick={onPatient}>
          <Avatar>{patient.name.split(' ').map((part) => part[0]).join('')}</Avatar>
          <Box className="patient-main"><Typography fontWeight={750}>{patient.name}</Typography><Typography fontSize={13} color="text.secondary">Дата рождения: {patient.birth}</Typography></Box>
          <Box><Typography fontSize={12} color="text.secondary">Телефон</Typography><Typography fontSize={14}>{patient.phone}</Typography></Box>
          <Chip label={patient.consent} size="small" color={patient.consent === 'Действует' ? 'success' : 'warning'} variant="outlined" />
          <ChevronRightRounded color="disabled" />
        </Box>) : <Box className="empty"><PersonSearchRounded /><Typography fontWeight={700}>Пациенты не найдены</Typography><Typography color="text.secondary">Проверьте запрос или создайте новую карточку</Typography></Box>}
      </Box>
    </>
  )
}

function PatientCard({ role, onBack, onVisit }: { role: Role; onBack: () => void; onVisit: () => void }) {
  return (
    <>
      <Button onClick={onBack} sx={{ mb: 2 }}>← К результатам поиска</Button>
      <Box className="patient-header">
        <Avatar sx={{ width: 64, height: 64, bgcolor: '#d9eee8', color: '#176b5b', fontSize: 22 }}>АМ</Avatar>
        <Box><Typography variant="h4" fontWeight={750}>Анна Михайлова</Typography><Typography color="text.secondary">14 мая 1991 · 35 лет · P-10482</Typography></Box>
        <Chip icon={<CheckCircleRounded />} label="Согласие действует" color="success" variant="outlined" />
      </Box>
      <Box className="patient-columns">
        <Box className="panel">
          <Typography className="panel-label">АДМИНИСТРАТИВНЫЕ ДАННЫЕ</Typography>
          <Info label="Телефон" value="+7 916 245-18-72" />
          <Info label="Электронная почта" value="a.mikhailova@example.ru" />
          <Info label="Предпочтительный канал" value="SMS · нейтральный шаблон" />
          <Divider /><Typography fontWeight={700} mt={2}>Ближайшая запись</Typography>
          <Box className="next-appointment"><EventNoteRounded /><Box><Typography fontWeight={700}>Сегодня, 09:00</Typography><Typography fontSize={13}>Александр Петров · первичный приём</Typography></Box></Box>
        </Box>
        <Box className="panel clinical-panel">
          <Box className="panel-title-row"><Typography className="panel-label">КЛИНИЧЕСКИЙ КОНТУР</Typography><LockOutlined fontSize="small" /></Box>
          {role === 'Врач' ? <>
            <Alert severity="success">Доступ разрешён: вы назначены на сегодняшний визит.</Alert>
            <Typography fontWeight={700} mt={2}>История документов</Typography>
            <Box className="history-item"><Box><Typography fontWeight={650}>Первичный осмотр</Typography><Typography fontSize={13} color="text.secondary">20 октября 2026 · черновик</Typography></Box><Button size="small" onClick={onVisit}>Открыть</Button></Box>
            <Box className="history-item"><Box><Typography fontWeight={650}>Консультация</Typography><Typography fontSize={13} color="text.secondary">18 сентября 2026 · подписано</Typography></Box><Chip size="small" icon={<LockOutlined />} label="Заблокирован" /></Box>
          </> : <>
            <Box className="locked-state"><LockOutlined /><Typography fontWeight={750}>Клинические данные недоступны</Typography><Typography fontSize={14}>Роль «Регистратор» не имеет доступа к жалобам, диагнозам, назначениям и медицинским документам.</Typography></Box>
          </>}
        </Box>
      </Box>
    </>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return <Box className="info-row"><Typography color="text.secondary" fontSize={13}>{label}</Typography><Typography fontWeight={600}>{value}</Typography></Box>
}

function Visit({ signed, diagnosis, prescription, setDiagnosis, setPrescription, onSave, onSign }: { signed: boolean; diagnosis: string; prescription: string; setDiagnosis: (v: string) => void; setPrescription: (v: string) => void; onSave: () => void; onSign: () => void }) {
  return (
    <>
      <Box className="visit-banner">
        <Box><Typography className="eyebrow">ПЕРВИЧНЫЙ ПРИЁМ · 09:00–09:50</Typography><Typography variant="h4" fontWeight={750}>Анна Михайлова</Typography><Typography color="text.secondary">35 лет · карта P-10482</Typography></Box>
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
            <Typography fontWeight={750}>Документ приёма</Typography>
            <Info label="Шаблон" value="Первичный осмотр · v2.4" />
            <Info label="Автор" value="Александр Петров" />
            <Info label="Последнее сохранение" value="10:02" />
            <Divider />
            <Typography fontSize={12} color="text.secondary" mt={2}>Подписание зафиксирует ровно показанную версию и контрольный хеш.</Typography>
            {!signed ? <Stack gap={1.2} mt={2}><Button variant="outlined" onClick={onSave}>Сохранить черновик</Button><Button variant="contained" startIcon={<LockOutlined />} onClick={onSign}>Проверить и подписать</Button></Stack> : <Button fullWidth variant="outlined" sx={{ mt: 2 }}>Создать дополнение</Button>}
          </Box>
        </Box>
      </Box>
    </>
  )
}

function EditIcon() { return <Box component="span" sx={{ width: 7, height: 7, borderRadius: '50%', bgcolor: 'currentColor' }} /> }
function FormSection({ number, title, children }: { number: string; title: string; children: React.ReactNode }) {
  return <Box className="form-section"><Box className="form-section-title"><Typography>{number}</Typography><Typography fontWeight={750}>{title}</Typography></Box>{children}</Box>
}

function BookingDialog({ open, onClose, onSave }: { open: boolean; onClose: () => void; onSave: () => void }) {
  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>Новая запись <IconButton onClick={onClose}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent>
      <Stack gap={2} mt={1}>
        <TextField label="Пациент" defaultValue="Анна Михайлова" />
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={2}><TextField label="Дата" type="date" defaultValue="2026-10-20" InputLabelProps={{ shrink: true }} fullWidth /><TextField label="Время" type="time" defaultValue="13:00" InputLabelProps={{ shrink: true }} fullWidth /></Stack>
        <TextField select label="Врач" defaultValue="petrov"><MenuItem value="petrov">Александр Петров</MenuItem><MenuItem value="lebedeva">Ольга Лебедева</MenuItem></TextField>
        <TextField select label="Услуга" defaultValue="repeat"><MenuItem value="repeat">Повторный приём · 30 мин</MenuItem><MenuItem value="first">Первичный приём · 50 мин</MenuItem></TextField>
        <Alert severity="info">Пациенту будет отправлено нейтральное сообщение без указания профиля клиники и цели визита.</Alert>
      </Stack>
    </DialogContent>
    <DialogActions><Button onClick={onClose}>Отмена</Button><Button variant="contained" onClick={onSave}>Создать запись</Button></DialogActions>
  </Dialog>
}

export default App
