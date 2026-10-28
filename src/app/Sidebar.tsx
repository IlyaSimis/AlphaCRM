import { ArticleRounded, BadgeRounded, CalendarMonthRounded, ContactPhoneRounded, FolderSharedRounded, HealthAndSafetyRounded, HistoryRounded, InsightsRounded, Inventory2Rounded, MedicalServicesRounded, OutboxRounded, PaymentsRounded, PeopleAltRounded, TodayRounded, TuneRounded } from '@mui/icons-material'
import { Avatar, Box, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import type { StaffItem } from '../api/types'
import type { VisitTarget } from '../features/visit/visit'
import { canWriteClinical, profileOf } from '../shared/roles'
import { hasTodayPage, navigationFor, pageTitles, startPage, type Page } from './pages'

const icons: Partial<Record<Page, ReactNode>> = {
  today: <TodayRounded />,
  schedule: <CalendarMonthRounded />,
  patients: <PeopleAltRounded />,
  cashier: <PaymentsRounded />,
  analytics: <InsightsRounded />,
  audit: <HistoryRounded />,
  registry: <ContactPhoneRounded />,
  settings: <TuneRounded />,
  staff: <BadgeRounded />,
  documents: <ArticleRounded />,
  outbox: <OutboxRounded />,
  inventory: <Inventory2Rounded />,
  drive: <FolderSharedRounded />,
}

function NavItem({ active, icon, label, onClick }: { active: boolean; icon: ReactNode; label: string; onClick: () => void }) {
  return <button type="button" className={`nav-item ${active ? 'active' : ''}`} onClick={onClick}>{icon}{label}</button>
}

type SidebarProps = {
  className: string
  page: Page
  staff: StaffItem
  live: boolean
  /** Открытые приёмы врача: можно вернуться к любому, ни один не закрывается при переходе. */
  visits: VisitTarget[]
  activeVisit: VisitTarget | null
  onNavigate: (page: Page) => void
  onVisit: (visit: VisitTarget) => void
}

export function Sidebar({ className, page, staff, live, visits, activeVisit, onNavigate, onVisit }: SidebarProps) {
  const profile = profileOf(staff)
  const role = staff.role
  // «Сегодня» — в своём блоке «Старт»; остальное меню — в прежнем порядке.
  const workplace = navigationFor(role).filter((item) => item !== 'today')
  return (
    <Box className={className}>
      {/* Название — кнопка «домой»: тот же переход, что после входа. Открытые приёмы не закрывает. */}
      <button type="button" className="brand brand-button" aria-label="На главную" onClick={() => onNavigate(startPage(role))}>
        <Box component="span" className="brand-mark"><HealthAndSafetyRounded /></Box>
        <Box component="span"><Typography component="span" className="brand-name">AlphaCRM</Typography><Typography component="span" className="brand-caption">медицинская система</Typography></Box>
      </button>
      {hasTodayPage(role) && <Box component="nav" aria-label="Старт" className="nav-section">
        <Typography className="nav-label">СТАРТ</Typography>
        <Stack spacing={0.7}>
          <NavItem active={page === 'today'} icon={icons.today} label={pageTitles.today} onClick={() => onNavigate('today')} />
        </Stack>
      </Box>}
      <Box component="nav" aria-label="Рабочее место" className="nav-section">
        <Typography className="nav-label">РАБОЧЕЕ МЕСТО</Typography>
        <Stack spacing={0.7}>
          {workplace.map((item) => <NavItem key={item} active={page === item || (item === 'patients' && page === 'patient')} icon={icons[item]} label={pageTitles[item]} onClick={() => onNavigate(item)} />)}
          {canWriteClinical(role) && visits.map((visit) => (
            <NavItem key={visit.appointmentId ?? visit.encounter?.encounterId ?? visit.patientId} active={activeVisit === visit} icon={<MedicalServicesRounded />} label={`Приём: ${visit.patientName}${visit.time ? ` · ${visit.time}` : ''}`} onClick={() => onVisit(visit)} />
          ))}
        </Stack>
      </Box>
      <Box className="profile">
        <Avatar sx={{ bgcolor: 'action.selected', color: 'primary.main' }}>{profile.initials}</Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}><Typography noWrap sx={{ fontWeight: 700, fontSize: 14 }}>{profile.name}</Typography><Typography sx={{ color: 'text.secondary', fontSize: 12 }}>{profile.role}{live ? '' : ' · демо'}</Typography></Box>
      </Box>
    </Box>
  )
}
