import { AdminPanelSettingsRounded, AutoAwesomeRounded, DashboardRounded, FolderSharedRounded, Inventory2Rounded, LogoutRounded, MenuRounded, PaymentsRounded, PeopleAltRounded } from '@mui/icons-material'
import { Box, Button, Chip, IconButton, Tab, Tabs, Typography } from '@mui/material'
import type { ReactElement } from 'react'
import { serviceTitles, type Service } from './pages'

const serviceIcons: Record<Service, ReactElement> = {
  dashboards: <DashboardRounded />,
  management: <AdminPanelSettingsRounded />,
  patients: <PeopleAltRounded />,
  inventory: <Inventory2Rounded />,
  finance: <PaymentsRounded />,
  drive: <FolderSharedRounded />,
}

type TopbarProps = {
  eyebrow: string
  title: string
  /** Сервисы, доступные роли, — вкладки верхней панели. */
  services: Service[]
  service: Service
  onService: (service: Service) => void
  /** false — демо-песочница без сервера. */
  live: boolean
  /** Клиника сотрудника — с сервера; в песочнице её нет. */
  clinicName?: string | null
  onAssistant: () => void
  onSignOut: () => void
  onMenu: () => void
}

/** Шапка: сервисы, ИИ-навигатор и выход. Тема и пароль находятся в настройках слева внизу. */
export function Topbar({ eyebrow, title, services, service, onService, live, clinicName, onAssistant, onSignOut, onMenu }: TopbarProps) {
  return (
    <Box className="topbar">
      <Box className="topbar-row">
        <IconButton className="mobile-menu" aria-label="Разделы" onClick={onMenu}><MenuRounded /></IconButton>
        <Tabs className="service-tabs" value={service} onChange={(_event: unknown, next: Service) => onService(next)} variant="scrollable" scrollButtons="auto" aria-label="Сервисы">
          {services.map((item) => <Tab key={item} value={item} icon={serviceIcons[item]} iconPosition="start" label={serviceTitles[item]} />)}
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
