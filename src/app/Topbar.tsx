import { DarkModeRounded, KeyRounded, LightModeRounded, LogoutRounded, MenuRounded } from '@mui/icons-material'
import { Box, Button, Chip, IconButton, Tooltip, Typography } from '@mui/material'
import { useColorMode } from './theme'

type TopbarProps = {
  eyebrow: string
  title: string
  /** false — демо-песочница без сервера. */
  live: boolean
  /** Клиника сотрудника — с сервера; в песочнице её нет. */
  clinicName?: string | null
  onPassword: () => void
  onSignOut: () => void
  onMenu: () => void
}

/** Шапка: кто вошёл, видно в боковой панели; здесь — тема, пароль и выход. Роль сменить нельзя. */
export function Topbar({ eyebrow, title, live, clinicName, onPassword, onSignOut, onMenu }: TopbarProps) {
  const { mode, toggle } = useColorMode()
  const themeLabel = mode === 'light' ? 'Включить тёмную тему' : 'Включить светлую тему'
  return (
    <Box className="topbar">
      <IconButton className="mobile-menu" onClick={onMenu}><MenuRounded /></IconButton>
      <Box><Typography className="eyebrow">{eyebrow}</Typography><Typography variant="h5" sx={{ fontWeight: 750 }}>{title}</Typography></Box>
      <Box className="role-switch">
        {live && clinicName && <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>{clinicName}</Typography>}
        {!live && <Chip size="small" color="warning" variant="outlined" label="Демо-песочница: данные не сохраняются" />}
        <Tooltip title={themeLabel}>
          <IconButton onClick={toggle} aria-label={themeLabel}>
            {mode === 'light' ? <DarkModeRounded /> : <LightModeRounded />}
          </IconButton>
        </Tooltip>
        {/* На узком экране подписи кнопок скрываются, имя для экранных чтецов остаётся. */}
        {live && <Button size="small" className="topbar-action" aria-label="Сменить пароль" startIcon={<KeyRounded />} onClick={onPassword}><span className="topbar-label">Сменить пароль</span></Button>}
        <Button size="small" className="topbar-action" variant="outlined" aria-label="Выйти" startIcon={<LogoutRounded />} onClick={onSignOut}><span className="topbar-label">Выйти</span></Button>
      </Box>
    </Box>
  )
}
