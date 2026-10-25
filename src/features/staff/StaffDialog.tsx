import { useState } from 'react'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import type { Practitioner, StaffRole } from '../../api/types'
import { clinicTimeZone } from '../../shared/clinicTime'
import { specialtyAndRoom } from '../../shared/practitioners'
import { roleLabels, staffRoles } from '../../shared/roles'
import { ShiftRows } from '../settings/ShiftRows'
import { toRows, type ShiftRow } from '../settings/shifts'
import { noShiftsYet, type StaffDraft } from './staffRules'

type StaffDialogProps = {
  /** Карточки врачей без учётной записи — только их можно привязать. */
  practitioners: Practitioner[]
  error: string
  onClose: () => void
  onSave: (draft: StaffDraft) => void
}

type CardMode = 'new' | 'existing'

export function StaffDialog({ practitioners, error, onClose, onSave }: StaffDialogProps) {
  const [fullName, setFullName] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState<StaffRole>('CASHIER')
  const [mode, setMode] = useState<CardMode>('new')
  const [practitionerId, setPractitionerId] = useState('')
  const [specialty, setSpecialty] = useState('')
  const [room, setRoom] = useState('')
  const [rows, setRows] = useState<ShiftRow[]>(() => toRows([]))
  const doctor = role === 'DOCTOR'
  const changeRow = (day: number, patch: Partial<ShiftRow>) => setRows((items) => items.map((row) => row.day === day ? { ...row, ...patch } : row))

  const draft = (): StaffDraft => ({
    fullName: fullName.trim(),
    username: username.trim(),
    password,
    role,
    practitionerId: doctor && mode === 'existing' && practitionerId ? practitionerId : null,
    newPractitioner: doctor && mode === 'new' ? { specialty, room, rows } : null,
  })

  return <Dialog open onClose={onClose} maxWidth={doctor && mode === 'new' ? 'sm' : 'xs'} fullWidth>
    <DialogTitle>Новый сотрудник</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField label="ФИО" value={fullName} onChange={(event) => setFullName(event.target.value)} helperText={doctor && mode === 'new' ? 'Одно ФИО для учётной записи и карточки врача' : undefined} />
        <TextField select label="Роль" value={role} onChange={(event) => setRole(event.target.value as StaffRole)}>
          {staffRoles.map((item) => <MenuItem key={item} value={item}>{roleLabels[item]}</MenuItem>)}
        </TextField>
        {doctor && <ToggleButtonGroup exclusive size="small" value={mode} onChange={(_, next: CardMode | null) => { if (next) setMode(next) }} aria-label="Карточка врача">
          <ToggleButton value="new">Новая карточка</ToggleButton>
          <ToggleButton value="existing">Уже есть в справочнике</ToggleButton>
        </ToggleButtonGroup>}
        {doctor && mode === 'existing' && (practitioners.length > 0
          ? <TextField select label="Карточка врача" value={practitionerId} onChange={(event) => setPractitionerId(event.target.value)}>
            {practitioners.map((item) => <MenuItem key={item.id} value={item.id}>{item.fullName} · {specialtyAndRoom(item).toLowerCase()}</MenuItem>)}
          </TextField>
          : <Typography sx={{ color: 'text.secondary', fontSize: 14 }}>Свободных карточек нет: у всех врачей справочника уже есть учётная запись.</Typography>)}
        {doctor && mode === 'new' && <>
          <Box sx={{ display: 'grid', gridTemplateColumns: '1fr 140px', gap: 2 }}>
            <TextField label="Специальность" value={specialty} onChange={(event) => setSpecialty(event.target.value)} slotProps={{ htmlInput: { maxLength: 120 } }} />
            <TextField label="Кабинет" value={room} onChange={(event) => setRoom(event.target.value)} slotProps={{ htmlInput: { maxLength: 80 } }} />
          </Box>
          <Box>
            <Typography sx={{ fontWeight: 650 }}>Недельный график</Typography>
            <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>Время клиники ({clinicTimeZone()}). Снятая галочка — выходной.</Typography>
            <ShiftRows rows={rows} label="Новый врач" onChange={changeRow} />
          </Box>
          {noShiftsYet(draft()) && <Alert severity="warning">Смен нет: карточка создастся, но записать к врачу нельзя, пока не задан график.</Alert>}
        </>}
        <TextField label="Логин" value={username} autoComplete="off" onChange={(event) => setUsername(event.target.value)} />
        <TextField label="Временный пароль" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} helperText="Не короче 8 символов. Покажем один раз; при первом входе сотрудник сменит его." />
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" onClick={() => onSave(draft())}>Создать</Button>
    </DialogActions>
  </Dialog>
}
