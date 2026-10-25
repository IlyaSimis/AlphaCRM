import { ArticleRounded, BadgeRounded, CalendarMonthRounded, ContactPhoneRounded, HealthAndSafetyRounded, HistoryRounded, InsightsRounded, MedicalServicesRounded, OutboxRounded, PaymentsRounded, PeopleAltRounded, ShieldOutlined, TuneRounded } from '@mui/icons-material'
import { Avatar, Box, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import type { StaffItem } from '../api/types'
import type { VisitTarget } from '../features/visit/visit'
import { profileOf } from '../shared/roles'
import { navigationFor, pageTitles, type Page } from './pages'

const icons: Partial<Record<Page, ReactNode>> = {
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
  return (
    <Box className={className}>
      <Box className="brand">
        <Box className="brand-mark"><HealthAndSafetyRounded /></Box>
        <Box><Typography className="brand-name">AlphaCRM</Typography><Typography className="brand-caption">медицинская система</Typography></Box>
      </Box>
      <Typography className="nav-label">РАБОЧЕЕ ПРОСТРАНСТВО</Typography>
      <Stack spacing={0.7}>
        {navigationFor(role).map((item) => <NavItem key={item} active={page === item || (item === 'patients' && page === 'patient')} icon={icons[item]} label={pageTitles[item]} onClick={() => onNavigate(item)} />)}
        {role === 'DOCTOR' && visits.map((visit) => (
          <NavItem key={visit.appointmentId ?? visit.encounter?.encounterId ?? visit.patientId} active={activeVisit === visit} icon={<MedicalServicesRounded />} label={`Приём: ${visit.patientName}${visit.time ? ` · ${visit.time}` : ''}`} onClick={() => onVisit(visit)} />
        ))}
      </Stack>
      <Box className="privacy-note">
        <ShieldOutlined />
        <Box><Typography sx={{ fontWeight: 700, fontSize: 13 }}>Данные разделены</Typography><Typography sx={{ fontSize: 12 }}>Доступ к клинической карте — только при участии в лечении</Typography></Box>
      </Box>
      <Box className="profile">
        <Avatar sx={{ bgcolor: 'action.selected', color: 'primary.main' }}>{profile.initials}</Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}><Typography noWrap sx={{ fontWeight: 700, fontSize: 14 }}>{profile.name}</Typography><Typography sx={{ color: 'text.secondary', fontSize: 12 }}>{role}{live ? '' : ' · демо'}</Typography></Box>
      </Box>
    </Box>
  )
}
