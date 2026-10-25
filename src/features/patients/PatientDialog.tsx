import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material'
import { emailProblem, type PatientInput } from '../../api/patients'
import type { AssignedStaff, PatientItem, StaffItem } from '../../api/types'
import { phoneSharedWith } from '../registry/registry'

type PatientDialogProps = {
  open: boolean
  mode?: 'create' | 'edit'
  /** Только для правки: новая карточка открывается пустой. */
  initial?: PatientInput
  error?: string
  /** Для предупреждения: телефон уже есть в другой карточке. */
  patients?: PatientItem[]
  selfId?: string
  /** Активные администраторы клиники — из них выбирается постоянный администратор. */
  administrators?: StaffItem[]
  /** Нынешний постоянный администратор: отключённый остаётся в списке с пометкой. */
  currentAssigned?: AssignedStaff | null
  onClose: () => void
  onSave: (input: PatientInput) => void
}

const NOBODY = ''

export function PatientDialog({ open, mode = 'create', initial, error = '', patients = [], selfId, administrators = [], currentAssigned = null, onClose, onSave }: PatientDialogProps) {
  const edit = mode === 'edit'
  const start = edit ? initial : undefined
  const [fullName, setFullName] = useState(start?.fullName ?? '')
  const [birthDate, setBirthDate] = useState(start?.birthDate ?? '')
  const [phone, setPhone] = useState(start?.phone ?? '')
  const [secondPhone, setSecondPhone] = useState(start?.secondPhone ?? '')
  const [email, setEmail] = useState(start?.email ?? '')
  const [assignedStaffId, setAssignedStaffId] = useState(start?.assignedStaffId ?? NOBODY)
  const duplicates = phoneSharedWith([phone, secondPhone], patients, selfId)
  const emailError = emailProblem(email)
  // Отключённого администратора заново не выбрать, но уже привязанный виден с пометкой.
  const keepsInactive = currentAssigned && !currentAssigned.active && !administrators.some((item) => item.id === currentAssigned.id)
  return <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
    <DialogTitle>{edit ? 'Данные пациента' : 'Новый пациент'}</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="ФИО" value={fullName} onChange={(event) => setFullName(event.target.value)} />
        <TextField label="Дата рождения" type="date" value={birthDate} onChange={(event) => setBirthDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField label="Телефон" value={phone} onChange={(event) => setPhone(event.target.value)} />
        <TextField label="Второй телефон" value={secondPhone} onChange={(event) => setSecondPhone(event.target.value)} helperText="Необязательно" />
        {duplicates.length > 0 && <Alert severity="warning">Этот телефон уже есть в карточке: {duplicates.map((item) => item.fullName).join(', ')}. Сохранить можно, но проверьте, не дубль ли это.</Alert>}
        <TextField label="E-mail" type="email" value={email} error={Boolean(emailError)} helperText={emailError ?? 'Необязательно'} onChange={(event) => setEmail(event.target.value)} slotProps={{ htmlInput: { maxLength: 255 } }} />
        <TextField select label="Постоянный администратор" value={assignedStaffId} onChange={(event) => setAssignedStaffId(event.target.value)}>
          <MenuItem value={NOBODY}>Не назначен</MenuItem>
          {keepsInactive && currentAssigned && <MenuItem value={currentAssigned.id}>{currentAssigned.fullName} · учётная запись отключена</MenuItem>}
          {administrators.map((item) => <MenuItem key={item.id} value={item.id}>{item.fullName}</MenuItem>)}
        </TextField>
        {edit && <Alert severity="info">В журнал аудита попадут только названия изменённых полей, без самих значений.</Alert>}
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={!fullName.trim() || !birthDate || !phone.trim() || Boolean(emailError)} onClick={() => onSave({ fullName: fullName.trim(), birthDate, phone: phone.trim(), secondPhone: secondPhone.trim(), email: email.trim(), assignedStaffId: assignedStaffId || null })}>{edit ? 'Сохранить изменения' : 'Сохранить пациента'}</Button>
    </DialogActions>
  </Dialog>
}
