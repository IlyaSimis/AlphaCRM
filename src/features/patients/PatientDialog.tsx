import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from '@mui/material'
import type { PatientInput } from '../../api/patients'

type PatientDialogProps = {
  open: boolean
  mode?: 'create' | 'edit'
  initial?: PatientInput
  error?: string
  onClose: () => void
  onSave: (input: PatientInput) => void
}

export function PatientDialog({ open, mode = 'create', initial, error = '', onClose, onSave }: PatientDialogProps) {
  const [fullName, setFullName] = useState(initial?.fullName ?? 'Ирина Белова')
  const [birthDate, setBirthDate] = useState(initial?.birthDate ?? '1994-03-12')
  const [phone, setPhone] = useState(initial?.phone ?? '+7 903 111-22-33')
  const edit = mode === 'edit'
  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle>{edit ? 'Данные пациента' : 'Новый пациент'}</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="ФИО" value={fullName} onChange={(event) => setFullName(event.target.value)} />
        <TextField label="Дата рождения" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label="Телефон" value={phone} onChange={(event) => setPhone(event.target.value)} />
        {edit && <Alert severity="info">В журнал аудита попадут только названия изменённых полей, без самих значений.</Alert>}
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={!fullName.trim() || !birthDate || !phone.trim()} onClick={() => onSave({ fullName: fullName.trim(), birthDate, phone: phone.trim() })}>{edit ? 'Сохранить изменения' : 'Сохранить пациента'}</Button>
    </DialogActions>
  </Dialog>
}
