import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material'
import type { Practitioner, StaffRole } from '../../api/types'
import { roleLabels, staffRoles } from '../../shared/roles'
import type { StaffDraft } from './staffRules'

type StaffDialogProps = {
  practitioners: Practitioner[]
  error: string
  onClose: () => void
  onSave: (draft: StaffDraft) => void
}

export function StaffDialog({ practitioners, error, onClose, onSave }: StaffDialogProps) {
  const [fullName, setFullName] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<StaffRole>('CASHIER')
  const [practitionerId, setPractitionerId] = useState('')
  return <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
    <DialogTitle>Новый сотрудник</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="ФИО" value={fullName} onChange={(event) => setFullName(event.target.value)} />
        <TextField label="Логин" value={username} autoComplete="off" onChange={(event) => setUsername(event.target.value)} />
        <TextField label="Временный пароль" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} helperText="Не короче 8 символов. Покажем один раз; при первом входе сотрудник сменит его." />
        <TextField select label="Роль" value={role} onChange={(event) => setRole(event.target.value as StaffRole)}>
          {staffRoles.map((item) => <MenuItem key={item} value={item}>{roleLabels[item]}</MenuItem>)}
        </TextField>
        {role === 'DOCTOR' && <TextField select label="Карточка врача" value={practitionerId} onChange={(event) => setPractitionerId(event.target.value)}>
          {practitioners.map((item) => <MenuItem key={item.id} value={item.id}>{item.fullName} · {item.specialty.toLowerCase()}</MenuItem>)}
        </TextField>}
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" onClick={() => onSave({ fullName: fullName.trim(), username: username.trim(), password, role, practitionerId: role === 'DOCTOR' && practitionerId ? practitionerId : null })}>Создать</Button>
    </DialogActions>
  </Dialog>
}
