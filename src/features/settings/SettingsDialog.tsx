import { DarkModeRounded, KeyRounded, LightModeRounded, SettingsRounded } from '@mui/icons-material'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Divider, FormControlLabel, Stack, Switch, Typography } from '@mui/material'
import type { StaffItem } from '../../api/types'
import { useColorMode } from '../../app/theme'

type SettingsDialogProps = {
  open: boolean
  staff: StaffItem
  live: boolean
  onPassword: () => void
  onClose: () => void
}

/** Личные настройки: внешний вид, данные текущей учётной записи и безопасность. */
export function SettingsDialog({ open, staff, live, onPassword, onClose }: SettingsDialogProps) {
  const { mode, toggle } = useColorMode()
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs" aria-labelledby="settings-title">
      <DialogTitle id="settings-title"><Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}><SettingsRounded color="primary" /><span>Настройки</span></Stack></DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} divider={<Divider flexItem />}>
          <Box>
            <Typography className="panel-label">ИНТЕРФЕЙС</Typography>
            <FormControlLabel
              sx={{ mt: 1, ml: 0, display: 'flex', justifyContent: 'space-between' }}
              labelPlacement="start"
              control={<Switch checked={mode === 'dark'} onChange={toggle} slotProps={{ input: { 'aria-label': 'Тёмная тема' } }} />}
              label={<Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>{mode === 'light' ? <LightModeRounded color="action" /> : <DarkModeRounded color="action" />}<span>Тёмная тема</span></Stack>}
            />
            <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Выбор сохраняется в этом браузере.</Typography>
          </Box>
          <Box>
            <Typography className="panel-label">УЧЁТНАЯ ЗАПИСЬ</Typography>
            <Typography sx={{ mt: 1, fontWeight: 650 }}>{staff.fullName}</Typography>
            <Typography sx={{ color: 'text.secondary' }}>{staff.username}</Typography>
            {staff.clinicName && <Typography sx={{ color: 'text.secondary', fontSize: 13 }}>Клиника: {staff.clinicName}</Typography>}
          </Box>
          <Box>
            <Typography className="panel-label">БЕЗОПАСНОСТЬ</Typography>
            {live
              ? <Button sx={{ mt: 1 }} variant="outlined" startIcon={<KeyRounded />} onClick={onPassword}>Сменить пароль</Button>
              : <Typography sx={{ mt: 1, color: 'text.secondary', fontSize: 13 }}>В песочнице пароли не хранятся. Смена пароля доступна после подключения к серверу.</Typography>}
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions><Button variant="contained" onClick={onClose}>Готово</Button></DialogActions>
    </Dialog>
  )
}
