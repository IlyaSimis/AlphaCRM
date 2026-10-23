import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from '@mui/material'
import { passwordProblem } from './password'

type PasswordDialogProps = {
  /** Смена своего пароля требует текущий; новый пароль сотруднику задаёт администратор без него. */
  requireCurrent: boolean
  title: string
  error: string
  onClose: () => void
  onSave: (input: { currentPassword: string; newPassword: string }) => void
}

export function PasswordDialog({ requireCurrent, title, error, onClose, onSave }: PasswordDialogProps) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [localError, setLocalError] = useState('')

  const submit = () => {
    const problem = passwordProblem(newPassword, repeat)
    if (problem) {
      setLocalError(problem)
      return
    }
    setLocalError('')
    onSave({ currentPassword, newPassword })
  }

  const shown = localError || error
  return <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
    <DialogTitle>{title}</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {shown && <Alert severity="error">{shown}</Alert>}
        {requireCurrent && <TextField label="Текущий пароль" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} />}
        <TextField label="Новый пароль" type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} helperText="Не короче 8 символов" />
        <TextField label="Повторите пароль" type="password" autoComplete="new-password" value={repeat} onChange={(event) => setRepeat(event.target.value)} />
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={(requireCurrent && !currentPassword) || !newPassword} onClick={submit}>Сохранить пароль</Button>
    </DialogActions>
  </Dialog>
}
