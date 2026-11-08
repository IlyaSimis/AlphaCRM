import { AdminPanelSettingsRounded, ChatBubbleOutlineRounded, ChevronLeftRounded, ChevronRightRounded, DashboardRounded, DescriptionRounded, FolderSharedRounded, HealthAndSafetyRounded, Inventory2Rounded, MedicalServicesRounded, PaymentsRounded, PeopleAltRounded, SettingsRounded } from '@mui/icons-material'
import { Avatar, Badge, Box, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import type { StaffItem } from '../api/types'
import type { VisitTarget } from '../features/visit/visitRules'
import { canWriteClinical, profileOf } from '../shared/roles'
import { serviceTitles, startPage, type Page, type Service } from './pages'
import { rolesOf } from '../shared/permissions'

const serviceIcons: Record<Service, ReactNode> = {
  dashboards: <DashboardRounded />, 'staff-chat': <ChatBubbleOutlineRounded />, management: <AdminPanelSettingsRounded />, patients: <PeopleAltRounded />,
  documents: <DescriptionRounded />, inventory: <Inventory2Rounded />, finance: <PaymentsRounded />, drive: <FolderSharedRounded />,
}

function NavItem({ active, icon, label, collapsed = false, badge, onClick }: { active: boolean; icon: ReactNode; label: string; collapsed?: boolean; badge?: number; onClick: () => void }) {
  const accessibleLabel = badge === undefined ? label : `${label}, непрочитанных: ${badge}`
  return <button type="button" className={`nav-item ${active ? 'active' : ''}`} aria-label={accessibleLabel} title={collapsed ? accessibleLabel : undefined} aria-current={active ? 'page' : undefined} onClick={onClick}>
    {badge === undefined ? icon : <Badge badgeContent={badge} max={99} color="primary" invisible={badge === 0}>{icon}</Badge>}
    <span className="nav-text">{label}</span>
  </button>
}

type SidebarProps = {
  className: string
  service: Service
  services: Service[]
  staff: StaffItem
  live: boolean
  visits: VisitTarget[]
  activeVisit: VisitTarget | null
  unreadMessages: number
  collapsed?: boolean
  onService: (service: Service) => void
  onNavigate: (page: Page) => void
  onVisit: (visit: VisitTarget) => void
  onSettings: () => void
  onToggleCollapse?: () => void
}

/** Боковое меню: сервисы, открытые приёмы и личные настройки. */
export function Sidebar({ className, service, services, staff, live, visits, activeVisit, unreadMessages, collapsed = false, onService, onNavigate, onVisit, onSettings, onToggleCollapse }: SidebarProps) {
  const profile = profileOf(staff)
  const role = rolesOf(staff)
  return (
    <Box className={`${className} ${collapsed ? 'sidebar-collapsed' : ''}`}>
      <Box className="sidebar-brand-row">
        <button type="button" className="brand brand-button" aria-label="На главную" onClick={() => onNavigate(startPage(role))}>
          <Box component="span" className="brand-mark"><HealthAndSafetyRounded /></Box>
          <Box component="span" className="brand-text"><Typography component="span" className="brand-name">AlphaCRM</Typography><Typography component="span" className="brand-caption">{serviceTitles[service]}</Typography></Box>
        </button>
        {onToggleCollapse && <Tooltip title={collapsed ? 'Развернуть меню' : 'Свернуть меню'}><IconButton className="sidebar-collapse" aria-label={collapsed ? 'Развернуть меню' : 'Свернуть меню'} onClick={onToggleCollapse}>{collapsed ? <ChevronRightRounded /> : <ChevronLeftRounded />}</IconButton></Tooltip>}
      </Box>
      <Box component="nav" aria-label="Сервисы" className="nav-section">
        <Typography className="nav-label">РАЗДЕЛЫ</Typography>
        <Stack spacing={0.7}>
          {services.map((item) => <NavItem key={item} active={item === service} collapsed={collapsed} icon={serviceIcons[item]} label={serviceTitles[item]} badge={item === 'staff-chat' ? unreadMessages : undefined} onClick={() => onService(item)} />)}
        </Stack>
      </Box>
      {canWriteClinical(role) && visits.length > 0 && <Box component="nav" aria-label="Открытые приёмы" className="nav-section">
        <Typography className="nav-label">ОТКРЫТЫЕ ПРИЁМЫ</Typography>
        <Stack spacing={0.7}>
          {visits.map((visit) => <NavItem key={visit.appointmentId ?? visit.encounter?.encounterId ?? visit.patientId} active={activeVisit === visit} collapsed={collapsed} icon={<MedicalServicesRounded />} label={`Приём: ${visit.patientName}${visit.time ? ` · ${visit.time}` : ''}`} onClick={() => onVisit(visit)} />)}
        </Stack>
      </Box>}
      <Box className="sidebar-bottom">
        <NavItem active={false} collapsed={collapsed} icon={<SettingsRounded />} label="Настройки" onClick={onSettings} />
        <Box className="profile">
          <Avatar sx={{ bgcolor: 'action.selected', color: 'primary.main' }}>{profile.initials}</Avatar>
          <Box className="profile-text" sx={{ minWidth: 0, flex: 1 }}><Typography noWrap sx={{ fontWeight: 700, fontSize: 14 }}>{profile.name}</Typography><Typography sx={{ color: 'text.secondary', fontSize: 12 }}>{profile.role}{live ? '' : ' · демо'}</Typography></Box>
        </Box>
      </Box>
    </Box>
  )
}
