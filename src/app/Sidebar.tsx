import { ArticleRounded, AssignmentTurnedInRounded, BadgeRounded, CalendarMonthRounded, ContactPhoneRounded, DeleteOutlineRounded, DescriptionRounded, EventNoteRounded, FolderSharedRounded, FolderRounded, HealthAndSafetyRounded, HistoryRounded, InsightsRounded, Inventory2Rounded, ListAltRounded, MedicalServicesRounded, OutboxRounded, PaymentsRounded, PeopleAltRounded, QrCode2Rounded, QueryStatsRounded, ReceiptLongRounded, SellRounded, TodayRounded } from '@mui/icons-material'
import { Avatar, Box, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'
import type { StaffItem } from '../api/types'
import type { VisitTarget } from '../features/visit/visit'
import { canWriteClinical, profileOf } from '../shared/roles'
import { pageTitles, sectionsFor, serviceOf, serviceTitles, startPage, type Page, type Service } from './pages'

export const pageIcons: Partial<Record<Page, ReactNode>> = {
  today: <TodayRounded />,
  analytics: <InsightsRounded />,
  staff: <BadgeRounded />,
  audit: <HistoryRounded />,
  outbox: <OutboxRounded />,
  schedule: <CalendarMonthRounded />,
  patients: <PeopleAltRounded />,
  registry: <ContactPhoneRounded />,
  documents: <ArticleRounded />,
  hours: <EventNoteRounded />,
  'stock-balances': <Inventory2Rounded />,
  'stock-items': <ListAltRounded />,
  'stock-documents': <ReceiptLongRounded />,
  'stock-marking': <QrCode2Rounded />,
  'stock-norms': <AssignmentTurnedInRounded />,
  cashier: <PaymentsRounded />,
  sales: <QueryStatsRounded />,
  prices: <SellRounded />,
  drive: <FolderRounded />,
  'drive-shared': <FolderSharedRounded />,
  'drive-trash': <DeleteOutlineRounded />,
}

function NavItem({ active, icon, label, onClick }: { active: boolean; icon: ReactNode; label: string; onClick: () => void }) {
  return <button type="button" className={`nav-item ${active ? 'active' : ''}`} aria-current={active ? 'page' : undefined} onClick={onClick}>{icon}{label}</button>
}

type SidebarProps = {
  className: string
  page: Page
  /** Сервис верхней панели: в меню — его разделы. */
  service: Service
  staff: StaffItem
  live: boolean
  /** Открытые приёмы врача: можно вернуться к любому, ни один не закрывается при переходе. */
  visits: VisitTarget[]
  activeVisit: VisitTarget | null
  onNavigate: (page: Page) => void
  onVisit: (visit: VisitTarget) => void
}

/** Боковое меню: разделы выбранного сервиса и открытые приёмы врача. */
export function Sidebar({ className, page, service, staff, live, visits, activeVisit, onNavigate, onVisit }: SidebarProps) {
  const profile = profileOf(staff)
  const role = staff.role
  const sections = sectionsFor(service, role)
  const current = page === 'patient' ? 'patients' : page
  return (
    <Box className={className}>
      {/* Название — кнопка «домой»: тот же переход, что после входа. Открытые приёмы не закрывает. */}
      <button type="button" className="brand brand-button" aria-label="На главную" onClick={() => onNavigate(startPage(role))}>
        <Box component="span" className="brand-mark"><HealthAndSafetyRounded /></Box>
        <Box component="span"><Typography component="span" className="brand-name">AlphaCRM</Typography><Typography component="span" className="brand-caption">{serviceTitles[service]}</Typography></Box>
      </button>
      {sections.map((section) => (
        <Box key={section.label} component="nav" aria-label={section.label.charAt(0) + section.label.slice(1).toLocaleLowerCase('ru-RU')} className="nav-section">
          <Typography className="nav-label">{section.label}</Typography>
          <Stack spacing={0.7}>
            {section.pages.map((item) => <NavItem key={item} active={current === item && serviceOf(item) === service} icon={pageIcons[item] ?? <DescriptionRounded />} label={pageTitles[item]} onClick={() => onNavigate(item)} />)}
          </Stack>
        </Box>
      ))}
      {canWriteClinical(role) && visits.length > 0 && <Box component="nav" aria-label="Открытые приёмы" className="nav-section">
        <Typography className="nav-label">ОТКРЫТЫЕ ПРИЁМЫ</Typography>
        <Stack spacing={0.7}>
          {visits.map((visit) => (
            <NavItem key={visit.appointmentId ?? visit.encounter?.encounterId ?? visit.patientId} active={activeVisit === visit} icon={<MedicalServicesRounded />} label={`Приём: ${visit.patientName}${visit.time ? ` · ${visit.time}` : ''}`} onClick={() => onVisit(visit)} />
          ))}
        </Stack>
      </Box>}
      <Box className="profile">
        <Avatar sx={{ bgcolor: 'action.selected', color: 'primary.main' }}>{profile.initials}</Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}><Typography noWrap sx={{ fontWeight: 700, fontSize: 14 }}>{profile.name}</Typography><Typography sx={{ color: 'text.secondary', fontSize: 12 }}>{profile.role}{live ? '' : ' · демо'}</Typography></Box>
      </Box>
    </Box>
  )
}
