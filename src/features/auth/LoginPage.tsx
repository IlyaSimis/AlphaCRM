import { useState } from 'react'
import { HealthAndSafetyRounded, LoginRounded, RefreshRounded } from '@mui/icons-material'
import { Alert, Box, Button, Divider, Stack, TextField, Typography } from '@mui/material'
import type { StaffItem } from '../../api/types'
import { roleLabels } from '../../shared/roles'

type LoginPageProps = {
  serverOnline: boolean
  message?: string
  /** Учётные записи песочницы по ролям — без имён людей. */
  sandboxStaff: StaffItem[]
  onSignIn: (username: string, password: string) => Promise<string | null>
  onDemo: (staff: StaffItem) => void
  onRetry: () => void
}

/**
 * Вход сотрудника клиники по логину и паролю. Без сервера — только песочница по ролям, честно
 * помеченная как демо: в ней нет ни людей, ни запросов к серверу.
 */
export function LoginPage({ serverOnline, message, sandboxStaff, onSignIn, onDemo, onRetry }: LoginPageProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: { preventDefault: () => void }) => {
    event.preventDefault()
    if (!username.trim() || !password) return
    setBusy(true)
    setError('')
    const problem = await onSignIn(username.trim(), password)
    setBusy(false)
    if (problem) {
      setError(problem)
      setPassword('')
    }
  }

  return (
    <Box className="login-page">
      <Box className="login-card panel">
        <Box className="brand">
          <Box className="brand-mark"><HealthAndSafetyRounded /></Box>
          <Box><Typography className="brand-name">AlphaCRM</Typography><Typography className="brand-caption">вход для сотрудников</Typography></Box>
        </Box>
        {message && <Alert severity="info">{message}</Alert>}
        <Box component="form" onSubmit={submit} noValidate>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField label="Логин" value={username} autoComplete="username" autoFocus onChange={(event) => setUsername(event.target.value)} />
            <TextField label="Пароль" type="password" value={password} autoComplete="current-password" onChange={(event) => setPassword(event.target.value)} />
            <Button type="submit" variant="contained" startIcon={<LoginRounded />} disabled={busy || !username.trim() || !password}>Войти</Button>
          </Stack>
        </Box>
        {!serverOnline && <>
          <Divider />
          <Alert severity="warning" action={<Button size="small" startIcon={<RefreshRounded />} onClick={onRetry}>Проверить</Button>}>
            Сервер недоступен. Можно посмотреть интерфейс в песочнице: данные выдуманы, живут только в этой вкладке и никуда не отправляются.
          </Alert>
          <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Открыть песочницу с ролью:</Typography>
          <Stack direction="row" spacing={1} className="demo-profiles" sx={{ flexWrap: 'wrap' }}>
            {sandboxStaff.map((staff) => (
              <Button key={staff.id} variant="outlined" size="small" onClick={() => onDemo(staff)}>Песочница: {roleLabels[staff.role]}</Button>
            ))}
          </Stack>
        </>}
      </Box>
    </Box>
  )
}
