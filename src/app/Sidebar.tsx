import { CalendarMonthRounded, HealthAndSafetyRounded, HistoryRounded, InsightsRounded, MedicalServicesRounded, PaymentsRounded, PeopleAltRounded, ShieldOutlined } from '@mui/icons-material'
import { Avatar, Box, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import { profileFor, type Role } from '../shared/roles'
import { navigationFor, pageTitles, type Page } from './pages'

const icons: Partial<Record<Page, ReactNode>> = {
  schedule: <CalendarMonthRounded />,
  patients: <PeopleAltRounded />,
  cashier: <PaymentsRounded />,
  analytics: <InsightsRounded />,
  audit: <HistoryRounded />,
}

function NavItem({ active, icon, label, onClick }: { active: boolean; icon: ReactNode; label: string; onClick: () => void }) {
  return <button type="button" className={`nav-item ${active ? 'active' : ''}`} onClick={onClick}>{icon}{label}</button>
}

export function Sidebar({ className, page, role, doctorId, visitOpen, onNavigate }: { className: string; page: Page; role: Role; doctorId: string; visitOpen: boolean; onNavigate: (page: Page) => void }) {
  const profile = profileFor(role, doctorId)
  return (
    <Box className={className}>
      <Box className="brand">
        <Box className="brand-mark"><HealthAndSafetyRounded /></Box>
        <Box><Typography className="brand-name">Тихая клиника</Typography><Typography className="brand-caption">медицинская система</Typography></Box>
      </Box>
      <Typography className="nav-label">РАБОЧЕЕ ПРОСТРАНСТВО</Typography>
      <Stack spacing={0.7}>
        {navigationFor(role).map((item) => <NavItem key={item} active={page === item || (item === 'patients' && page === 'patient')} icon={icons[item]} label={pageTitles[item]} onClick={() => onNavigate(item)} />)}
        {role === 'Врач' && visitOpen && <NavItem active={page === 'visit'} icon={<MedicalServicesRounded />} label="Текущий приём" onClick={() => onNavigate('visit')} />}
      </Stack>
      <Box className="privacy-note">
        <ShieldOutlined />
        <Box><Typography sx={{ fontWeight: 700, fontSize: 13 }}>Данные разделены</Typography><Typography sx={{ fontSize: 12 }}>Доступ к клинической карте — только при участии в лечении</Typography></Box>
      </Box>
      <Box className="profile">
        <Avatar sx={{ bgcolor: 'action.selected', color: 'primary.main' }}>{profile.initials}</Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}><Typography noWrap sx={{ fontWeight: 700, fontSize: 14 }}>{profile.name}</Typography><Typography sx={{ color: 'text.secondary', fontSize: 12 }}>{role}</Typography></Box>
      </Box>
    </Box>
  )
}
