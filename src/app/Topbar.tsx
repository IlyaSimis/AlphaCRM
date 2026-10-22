import { DarkModeRounded, LightModeRounded, MenuRounded } from '@mui/icons-material'
import { Box, FormControl, IconButton, MenuItem, Select, Tooltip, Typography } from '@mui/material'
import { doctors, roles, type Role } from '../shared/roles'
import { useColorMode } from './theme'

type TopbarProps = {
  eyebrow: string
  title: string
  role: Role
  doctorId: string
  onRole: (role: Role) => void
  onDoctor: (doctorId: string) => void
  onMenu: () => void
}

export function Topbar({ eyebrow, title, role, doctorId, onRole, onDoctor, onMenu }: TopbarProps) {
  const { mode, toggle } = useColorMode()
  const themeLabel = mode === 'light' ? 'Включить тёмную тему' : 'Включить светлую тему'
  return (
    <Box className="topbar">
      <IconButton className="mobile-menu" onClick={onMenu}><MenuRounded /></IconButton>
      <Box><Typography className="eyebrow">{eyebrow}</Typography><Typography variant="h5" sx={{ fontWeight: 750 }}>{title}</Typography></Box>
      <Box className="role-switch">
        <Tooltip title={themeLabel}>
          <IconButton onClick={toggle} aria-label={themeLabel}>
            {mode === 'light' ? <DarkModeRounded /> : <LightModeRounded />}
          </IconButton>
        </Tooltip>
        <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>Режим просмотра</Typography>
        <FormControl size="small">
          <Select value={role} onChange={(event) => onRole(event.target.value as Role)} aria-label="Роль пользователя">
            {roles.map((item) => <MenuItem key={item} value={item}>{item}</MenuItem>)}
          </Select>
        </FormControl>
        {role === 'Врач' && <FormControl size="small">
          <Select value={doctorId} onChange={(event) => onDoctor(event.target.value)} aria-label="Работать от имени врача">
            {doctors.map((doctor) => <MenuItem key={doctor.id} value={doctor.id}>{doctor.name}</MenuItem>)}
          </Select>
        </FormControl>}
      </Box>
    </Box>
  )
}
