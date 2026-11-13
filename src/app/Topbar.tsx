import { ArticleRounded, AssignmentTurnedInRounded, AutoAwesomeRounded, BadgeRounded, CalendarMonthRounded, ChatBubbleOutlineRounded, ContactPhoneRounded, DeleteOutlineRounded, DescriptionRounded, EventNoteRounded, FolderRounded, FolderSharedRounded, HealthAndSafetyRounded, HistoryRounded, InsightsRounded, Inventory2Rounded, ListAltRounded, LogoutRounded, MenuRounded, OutboxRounded, PaymentsRounded, PeopleAltRounded, QrCode2Rounded, QueryStatsRounded, ReceiptLongRounded, SellRounded, TodayRounded, WorkOutlineRounded, WorkRounded } from '@mui/icons-material'
import { Badge, Box, Button, Chip, IconButton, Tab, Tabs, Typography } from '@mui/material'
import type { ReactElement } from 'react'
import { pageTitles, type Page } from './pages'

const pageIcons: Partial<Record<Page, ReactElement>> = {
  today: <TodayRounded />, workspace: <AssignmentTurnedInRounded />, messages: <ChatBubbleOutlineRounded />, analytics: <InsightsRounded />,
  staff: <BadgeRounded />, hr: <WorkRounded />, 'hr-me': <WorkOutlineRounded />, audit: <HistoryRounded />,
  'break-glass': <HealthAndSafetyRounded />, outbox: <OutboxRounded />, schedule: <CalendarMonthRounded />,
  patients: <PeopleAltRounded />, registry: <ContactPhoneRounded />, documents: <ArticleRounded />,
  'contract-templates': <DescriptionRounded />, hours: <EventNoteRounded />, 'stock-balances': <Inventory2Rounded />,
  'stock-items': <ListAltRounded />, 'stock-documents': <ReceiptLongRounded />, 'stock-marking': <QrCode2Rounded />,
  'stock-norms': <AssignmentTurnedInRounded />, cashier: <PaymentsRounded />, sales: <QueryStatsRounded />,
  prices: <SellRounded />, drive: <FolderRounded />, 'drive-shared': <FolderSharedRounded />, 'drive-trash': <DeleteOutlineRounded />,
}

type TopbarProps = {
  eyebrow: string
  title: string
  /** Разделы выбранного в левом меню сервиса. */
  pages: Page[]
  page: Page
  unreadMessages: number
  onNavigate: (page: Page) => void
  live: boolean
  clinicName?: string | null
  onAssistant: () => void
  onSignOut: () => void
  onMenu: () => void
}

/** Шапка: разделы текущего сервиса, ИИ-навигатор и выход. */
export function Topbar({ eyebrow, title, pages, page, unreadMessages, onNavigate, live, clinicName, onAssistant, onSignOut, onMenu }: TopbarProps) {
  const current = page === 'patient' ? 'patients' : page === 'visit' ? 'schedule' : page
  return (
    <Box className="topbar">
      <Box className="topbar-row">
        <IconButton className="mobile-menu" aria-label="Сервисы" onClick={onMenu}><MenuRounded /></IconButton>
        <Tabs className="service-tabs" value={current} onChange={(_event: unknown, next: Page) => onNavigate(next)} variant="scrollable" scrollButtons="auto" aria-label="Разделы сервиса">
          {pages.map((item) => <Tab key={item} value={item} icon={item === 'messages' ? <Badge badgeContent={unreadMessages || null} max={99} color="primary" invisible={unreadMessages === 0}><ChatBubbleOutlineRounded /></Badge> : pageIcons[item] ?? <DescriptionRounded />} iconPosition="start" label={pageTitles[item]} aria-label={item === 'messages' ? `StaffChat, непрочитанных: ${unreadMessages}` : undefined} onClick={() => { if (item === current && item !== page) onNavigate(item) }} />)}
        </Tabs>
        <Box className="role-switch">
          {live && clinicName && <Typography className="topbar-clinic" sx={{ fontSize: 13, color: 'text.secondary' }}>{clinicName}</Typography>}
          {live && <Chip size="small" color="success" variant="outlined" label="API подключён" />}
          {!live && <Chip size="small" color="warning" variant="outlined" label="Демо: общие данные в памяти, пространство — локально" />}
          <Button size="small" className="topbar-action" color="primary" variant="contained" aria-label="ИИ-навигатор" startIcon={<AutoAwesomeRounded />} onClick={onAssistant}><span className="topbar-label">ИИ-навигатор</span></Button>
          <Button size="small" className="topbar-action" variant="outlined" aria-label="Выйти" startIcon={<LogoutRounded />} onClick={onSignOut}><span className="topbar-label">Выйти</span></Button>
        </Box>
      </Box>
      {(eyebrow || title) && <Box className="topbar-title"><Typography className="eyebrow">{eyebrow}</Typography>{title && <Typography variant="h5" sx={{ fontWeight: 750 }}>{title}</Typography>}</Box>}
    </Box>
  )
}
