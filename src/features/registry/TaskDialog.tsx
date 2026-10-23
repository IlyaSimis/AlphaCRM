import { useState } from 'react'
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material'
import type { PatientItem, StaffItem } from '../../api/types'
import { roleLabels } from '../../shared/roles'
import { TASK_TITLE_MAX, taskTitleProblem } from './registry'
import type { TaskDraft } from './useRegistry'

type TaskDialogProps = {
  patients: PatientItem[]
  staff: StaffItem[]
  /** Карточка пациента: пациент уже выбран. */
  patientId?: string
  defaultAssigneeId: string
  today: string
  error: string
  onClose: () => void
  onSave: (draft: TaskDraft) => void
}

/** Значение пункта «Без пациента» в списке: задача не про конкретного пациента. */
const NO_PATIENT = ''

export function TaskDialog({ patients, staff, patientId, defaultAssigneeId, today, error, onClose, onSave }: TaskDialogProps) {
  const [patient, setPatient] = useState(patientId ?? NO_PATIENT)
  const [title, setTitle] = useState('')
  const [touched, setTouched] = useState(false)
  const [dueDate, setDueDate] = useState(today)
  const [assigneeId, setAssigneeId] = useState(staff.some((item) => item.id === defaultAssigneeId) ? defaultAssigneeId : staff[0]?.id ?? '')
  const problem = taskTitleProblem(title)
  const save = () => {
    setTouched(true)
    if (problem || !dueDate || !assigneeId) return
    onSave({ title: title.trim(), dueDate, assigneeId, patientId: patient || null })
  }
  return <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
    <DialogTitle>Новая задача регистратуры</DialogTitle>
    <DialogContent>
      <Stack spacing={2} sx={{ mt: 1 }}>
        {error && <Alert severity="error">{error}</Alert>}
        <TextField
          label="Что сделать"
          value={title}
          autoFocus
          multiline
          minRows={2}
          onChange={(event) => setTitle(event.target.value)}
          onBlur={() => setTouched(true)}
          error={touched && problem !== null}
          helperText={touched && problem ? problem : `${title.trim().length}/${TASK_TITLE_MAX}`}
          slotProps={{ htmlInput: { maxLength: TASK_TITLE_MAX + 50 } }}
        />
        <TextField label="Срок" type="date" value={dueDate} onChange={(event) => setDueDate(event.target.value)} slotProps={{ inputLabel: { shrink: true } }} />
        <TextField select label="Исполнитель" value={assigneeId} onChange={(event) => setAssigneeId(event.target.value)}>
          {staff.map((item) => <MenuItem key={item.id} value={item.id}>{item.fullName} · {roleLabels[item.role].toLowerCase()}</MenuItem>)}
        </TextField>
        {!patientId && <TextField select label="Пациент" value={patient} onChange={(event) => setPatient(event.target.value)} slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}>
          <MenuItem value={NO_PATIENT}>Без пациента</MenuItem>
          {patients.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
        </TextField>}
        <Alert severity="info">Это напоминание сотруднику. Пациенту ничего не отправляется.</Alert>
      </Stack>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose}>Отмена</Button>
      <Button variant="contained" disabled={!dueDate || !assigneeId} onClick={save}>Создать задачу</Button>
    </DialogActions>
  </Dialog>
}
