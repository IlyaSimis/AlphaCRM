import { useState } from 'react'
import { HealthAndSafetyRounded, KeyRounded, LogoutRounded } from '@mui/icons-material'
import { Alert, Box, Button, Stack, TextField, Typography } from '@mui/material'
import { changePassword } from '../../api/auth'
import type { StaffItem } from '../../api/types'
import { passwordProblem } from './password'

type ForcePasswordPageProps = {
  staff: StaffItem
  onDone: () => void
  onSignOut: () => void
}

/**
 * Вход с временным паролем: до смены пароля остальные страницы закрыты (сервер отвечает
 * PASSWORD_CHANGE_REQUIRED). Новый пароль — не короче 8 символов и не равен временному.
 */
export function ForcePasswordPage({ staff, onDone, onSignOut }: ForcePasswordPageProps) {
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (event: { preventDefault: () => void }) => {
    event.preventDefault()
    const problem = passwordProblem(next, repeat) ?? (next === current ? 'Новый пароль должен отличаться от временного' : null)
    if (problem) {
      setError(problem)
      return
    }
    setBusy(true)
    setError('')
    try {
      await changePassword(current, next)
      onDone()
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Box className="login-page">
      <Box className="login-card panel">
        <Box className="brand">
          <Box className="brand-mark"><HealthAndSafetyRounded /></Box>
          <Box><Typography className="brand-name">{staff.clinicName ?? 'AlphaCRM'}</Typography><Typography className="brand-caption">{staff.fullName} · {staff.username}</Typography></Box>
        </Box>
        <Alert severity="warning">Вы вошли с временным паролем. Задайте свой пароль — до этого остальные страницы закрыты.</Alert>
        <Box component="form" onSubmit={submit} noValidate>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField label="Временный пароль" type="password" autoComplete="current-password" value={current} onChange={(event) => setCurrent(event.target.value)} />
            <TextField label="Новый пароль" type="password" autoComplete="new-password" value={next} onChange={(event) => setNext(event.target.value)} helperText="Не короче 8 символов" />
            <TextField label="Повторите пароль" type="password" autoComplete="new-password" value={repeat} onChange={(event) => setRepeat(event.target.value)} />
            <Button type="submit" variant="contained" startIcon={<KeyRounded />} disabled={busy || !current || !next}>Сменить пароль</Button>
            <Button startIcon={<LogoutRounded />} onClick={onSignOut}>Выйти</Button>
          </Stack>
        </Box>
      </Box>
    </Box>
  )
}
