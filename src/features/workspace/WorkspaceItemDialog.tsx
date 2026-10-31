import { useState } from 'react'
import { AddRounded, DeleteOutlineRounded, EditRounded } from '@mui/icons-material'
import { Alert, Autocomplete, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, MenuItem, Stack, TextField, Typography } from '@mui/material'
import type { PatientItem, ScheduleItem, WorkspaceItem, WorkspaceItemInput } from '../../api/types'
import { formatClinicMoment } from '../../shared/clinicTime'
import { blankWorkspaceItem, inputOf, workspaceDateInput, workspaceInstant } from './workspace'

const typeOptions = ['Задача', 'Заметка', 'Идея', 'Встреча', 'Чек-лист', 'Проект']
const statusOptions = ['Без статуса', 'К выполнению', 'В работе', 'Жду', 'Заметки', 'Готово']
const colorOptions = [{ value: 'green', label: 'Зелёный' }, { value: 'blue', label: 'Синий' }, { value: 'orange', label: 'Оранжевый' }, { value: 'red', label: 'Красный' }, { value: 'purple', label: 'Фиолетовый' }, { value: 'gray', label: 'Серый' }]

type WorkspaceItemDialogProps = {
  item: WorkspaceItem | null
  preset?: string
  patients: PatientItem[]
  appointments: ScheduleItem[]
  onClose: () => void
  onSave: (input: WorkspaceItemInput) => Promise<void>
}

/** Существующая запись открывается для чтения; форма появляется только по явной команде. */
export function WorkspaceItemDialog({ item, preset, patients, appointments, onClose, onSave }: WorkspaceItemDialogProps) {
  const [draft, setDraft] = useState<WorkspaceItemInput>(() => item ? inputOf(item) : blankWorkspaceItem(preset))
  const [editing, setEditing] = useState(!item)
  const [tagInput, setTagInput] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const patch = <K extends keyof WorkspaceItemInput>(key: K, value: WorkspaceItemInput[K]) => setDraft((current) => ({ ...current, [key]: value }))
  const patient = patients.find((entry) => entry.id === draft.patientId) ?? null
  const appointment = appointments.find((entry) => entry.id === draft.appointmentId) ?? null
  const viewing = Boolean(item && !editing)

  const save = async () => {
    if (!draft.title.trim()) {
      setError('Введите название')
      return
    }
    const tags = [...new Set([...draft.tags, tagInput].map((tag) => tag.trim()).filter(Boolean))]
    setSaving(true)
    setError('')
    try {
      await onSave({ ...draft, title: draft.title.trim(), type: draft.type.trim() || 'Запись', status: draft.status.trim() || 'Без статуса', tags })
    } catch (reason) {
      setError((reason as Error).message)
      setSaving(false)
    }
  }

  return (
    <Dialog open onClose={saving ? undefined : onClose} maxWidth="md" fullWidth scroll="paper">
      <DialogTitle>{viewing ? item?.type : item ? 'Редактировать запись' : 'Новая запись'}</DialogTitle>
      <DialogContent dividers>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {viewing ? <Stack spacing={2.2} sx={{ pt: 0.5 }}>
          <Box><Typography variant="h5" sx={{ fontWeight: 750, overflowWrap: 'anywhere' }}>{draft.title}</Typography><Stack direction="row" spacing={0.8} useFlexGap sx={{ mt: 1.2, flexWrap: 'wrap' }}><Chip size="small" label={draft.status} />{draft.completed && <Chip size="small" color="success" label="Выполнено" />}{draft.tags.map((tag) => <Chip key={tag} size="small" variant="outlined" label={`#${tag}`} />)}</Stack></Box>
          {(draft.dueAt || draft.remindAt) && <Box><Typography className="panel-label" sx={{ mb: 0.5 }}>СРОКИ</Typography>{draft.dueAt && <Typography>Срок: {formatClinicMoment(draft.dueAt)}</Typography>}{draft.remindAt && <Typography>Напоминание: {formatClinicMoment(draft.remindAt)}</Typography>}</Box>}
          {draft.body && <Box><Typography className="panel-label" sx={{ mb: 0.5 }}>ТЕКСТ</Typography><Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{draft.body}</Typography></Box>}
          {(patient || appointment) && <Box><Typography className="panel-label" sx={{ mb: 0.5 }}>СВЯЗИ</Typography>{patient && <Typography>Пациент: {patient.name}</Typography>}{appointment && <Typography>Запись: {appointment.date} {appointment.time} · {appointment.name}</Typography>}</Box>}
          {draft.checklist.length > 0 && <Box><Typography className="panel-label" sx={{ mb: 0.5 }}>ЧЕК-ЛИСТ</Typography>{draft.checklist.map((entry, index) => <FormControlLabel key={index} control={<Checkbox checked={entry.done} disabled />} label={entry.text || `Пункт ${index + 1}`} />)}</Box>}
          {draft.fields.length > 0 && <Box><Typography className="panel-label" sx={{ mb: 0.5 }}>ДОПОЛНИТЕЛЬНЫЕ ПОЛЯ</Typography>{draft.fields.map((field, index) => <Typography key={index} sx={{ mb: 0.5 }}><strong>{field.name || `Поле ${index + 1}`}:</strong> {field.value}</Typography>)}</Box>}
        </Stack> : <Stack spacing={2} sx={{ pt: 0.5 }}>
          <TextField autoFocus required fullWidth label="Название" value={draft.title} onChange={(event) => patch('title', event.target.value)} slotProps={{ htmlInput: { maxLength: 240 } }} />
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <Autocomplete freeSolo options={typeOptions} value={draft.type} onChange={(_, value) => patch('type', value ?? '')} onInputChange={(_, value) => patch('type', value)} renderInput={(params) => <TextField {...params} label="Тип — можно свой" />} />
            <Autocomplete freeSolo options={statusOptions} value={draft.status} onChange={(_, value) => patch('status', value ?? '')} onInputChange={(_, value) => patch('status', value)} renderInput={(params) => <TextField {...params} label="Статус — можно свой" />} />
            <TextField select label="Цвет" value={draft.color} onChange={(event) => patch('color', event.target.value as WorkspaceItemInput['color'])}>{colorOptions.map((color) => <MenuItem key={color.value} value={color.value}>{color.label}</MenuItem>)}</TextField>
            <FormControlLabel control={<Checkbox checked={draft.completed} onChange={(event) => patch('completed', event.target.checked)} />} label="Выполнено" sx={{ mx: 0 }} />
            <TextField type="datetime-local" label="Срок" value={workspaceDateInput(draft.dueAt)} onChange={(event) => patch('dueAt', workspaceInstant(event.target.value))} slotProps={{ inputLabel: { shrink: true } }} />
            <TextField type="datetime-local" label="Время напоминания" value={workspaceDateInput(draft.remindAt)} onChange={(event) => patch('remindAt', workspaceInstant(event.target.value))} slotProps={{ inputLabel: { shrink: true } }} helperText="Напоминание появится в личном списке" />
          </Box>
          <TextField fullWidth multiline minRows={4} label="Текст" placeholder="Свободный текст, ссылки, план — без обязательной структуры" value={draft.body} onChange={(event) => patch('body', event.target.value)} slotProps={{ htmlInput: { maxLength: 30000 } }} />
          <Autocomplete<string, true, false, true> multiple freeSolo options={[]} value={draft.tags} inputValue={tagInput} onInputChange={(_, value) => setTagInput(value)} onChange={(_, value) => { patch('tags', value.map((tag) => tag.trim()).filter(Boolean)); setTagInput('') }} renderValue={(value, getItemProps) => value.map((tag, index) => <Chip {...getItemProps({ index })} key={`${tag}-${index}`} size="small" label={tag} />)} renderInput={(params) => <TextField {...params} label="Теги" placeholder="Введите тег и сохраните запись" />} />
          {(patients.length > 0 || appointments.length > 0) && <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            {patients.length > 0 && <Autocomplete options={patients} value={patient} getOptionLabel={(option) => option.name} isOptionEqualToValue={(left, right) => left.id === right.id} onChange={(_, value) => { patch('patientId', value?.id ?? null); if (!value) patch('appointmentId', null) }} renderInput={(params) => <TextField {...params} label="Пациент — необязательно" />} />}
            {appointments.length > 0 && <Autocomplete options={appointments.filter((entry) => !draft.patientId || entry.patientId === draft.patientId)} value={appointment} getOptionLabel={(option) => `${option.date} ${option.time} · ${option.name}`} isOptionEqualToValue={(left, right) => left.id === right.id} onChange={(_, value) => { patch('appointmentId', value?.id ?? null); if (value?.patientId) patch('patientId', value.patientId) }} renderInput={(params) => <TextField {...params} label="Запись в расписании — необязательно" />} />}
          </Box>}
          <Box><Typography className="panel-label" sx={{ mb: 1 }}>ЧЕК-ЛИСТ</Typography>{draft.checklist.map((entry, index) => <Stack direction="row" spacing={1} key={index} sx={{ alignItems: 'center', mb: 0.5 }}><Checkbox checked={entry.done} onChange={(event) => patch('checklist', draft.checklist.map((current, at) => at === index ? { ...current, done: event.target.checked } : current))} slotProps={{ input: { 'aria-label': `Пункт ${index + 1} выполнен` } }} /><TextField fullWidth size="small" value={entry.text} onChange={(event) => patch('checklist', draft.checklist.map((current, at) => at === index ? { ...current, text: event.target.value } : current))} placeholder={`Пункт ${index + 1}`} /><IconButton aria-label={`Удалить пункт ${index + 1}`} onClick={() => patch('checklist', draft.checklist.filter((_, at) => at !== index))}><DeleteOutlineRounded /></IconButton></Stack>)}<Button startIcon={<AddRounded />} onClick={() => patch('checklist', [...draft.checklist, { text: '', done: false }])}>Добавить пункт</Button></Box>
          <Box><Typography className="panel-label" sx={{ mb: 1 }}>СВОИ ПОЛЯ</Typography>{draft.fields.map((field, index) => <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} key={index} sx={{ mb: 1 }}><TextField size="small" label="Название поля" value={field.name} onChange={(event) => patch('fields', draft.fields.map((current, at) => at === index ? { ...current, name: event.target.value } : current))} sx={{ sm: { minWidth: 220 } }} /><TextField fullWidth size="small" label="Значение" value={field.value} onChange={(event) => patch('fields', draft.fields.map((current, at) => at === index ? { ...current, value: event.target.value } : current))} /><IconButton aria-label={`Удалить поле ${index + 1}`} onClick={() => patch('fields', draft.fields.filter((_, at) => at !== index))}><DeleteOutlineRounded /></IconButton></Stack>)}<Button startIcon={<AddRounded />} onClick={() => patch('fields', [...draft.fields, { name: '', value: '' }])}>Добавить поле</Button></Box>
        </Stack>}
      </DialogContent>
      <DialogActions>{viewing ? <><Button onClick={onClose}>Закрыть</Button><Button variant="contained" startIcon={<EditRounded />} onClick={() => setEditing(true)}>Редактировать</Button></> : <><Button onClick={onClose} disabled={saving}>Отмена</Button><Button variant="contained" onClick={() => void save()} disabled={saving}>{saving ? 'Сохранение…' : 'Сохранить'}</Button></>}</DialogActions>
    </Dialog>
  )
}
