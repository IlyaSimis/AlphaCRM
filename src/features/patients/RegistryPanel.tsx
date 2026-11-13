import { useEffect, useState } from 'react'
import { AddRounded, EditRounded } from '@mui/icons-material'
import { Alert, Box, Button, Divider, Typography } from '@mui/material'
import type { PatientItem, RegistryInput, TaskItem } from '../../api/types'
import { localToday } from '../../shared/dates'
import { Info } from '../../shared/ui'
import { referralLabel } from '../registry/registry'
import { TaskDialog } from '../registry/TaskDialog'
import { TaskList } from '../registry/TaskList'
import type { Registry } from '../registry/useRegistry'
import { RegistryDialog } from './RegistryDialog'
import { MessengerPanel } from './MessengerPanel'

type RegistryPanelProps = {
  patient: PatientItem
  /** Карточки с тем же телефоном: с сервера или посчитанные локально в демо-режиме. */
  sharedWith: Array<{ id: string; fullName: string }>
  admin: boolean
  selfId: string
  registry: Registry
  onOpenPatient: (id: string) => void
  onSave: (input: RegistryInput) => Promise<string | null>
}

/** Административная часть карточки: источник, заметка, задачи. В клинический контур не попадает. */
export function RegistryPanel({ patient, sharedWith, admin, selfId, registry, onOpenPatient, onSave }: RegistryPanelProps) {
  const [editing, setEditing] = useState(false)
  const [editError, setEditError] = useState('')
  const [taskDialog, setTaskDialog] = useState(false)
  const [taskError, setTaskError] = useState('')
  const [tasks, setTasks] = useState<TaskItem[]>([])
  const [tasksError, setTasksError] = useState('')
  const { patientTasks, revision } = registry
  const today = localToday()

  useEffect(() => {
    let cancelled = false
    patientTasks(patient.id)
      .then((items) => { if (!cancelled) { setTasks(items); setTasksError('') } })
      .catch((reason: Error) => { if (!cancelled) setTasksError(reason.message) })
    return () => { cancelled = true }
  }, [patient.id, patientTasks, revision])

  return (
    <>
      {sharedWith.length > 0 && (
        <Alert severity="warning" sx={{ my: 1 }}>
          Этот телефон уже указан в другой карточке:{' '}
          {sharedWith.map((other, index) => <span key={other.id}>{index > 0 && ', '}<Button size="small" sx={{ p: 0, minWidth: 0, verticalAlign: 'baseline' }} onClick={() => onOpenPatient(other.id)}>{other.fullName}</Button></span>)}
          . Проверьте, не дубль ли это.
        </Alert>
      )}
      <Divider sx={{ my: 2 }} />
      <Box className="panel-title-row">
        <Typography sx={{ fontWeight: 700 }}>Регистратура</Typography>
        {admin && <Button size="small" startIcon={<EditRounded />} onClick={() => { setEditError(''); setEditing(true) }}>Изменить</Button>}
      </Box>
      <Info label="Откуда пришёл" value={referralLabel(patient.referralSource)} />
      <Info label="Заметка регистратуры" value={patient.registryNote || '—'} />
      <MessengerPanel patient={patient} admin={admin} />
      <Box className="panel-title-row" sx={{ mt: 2 }}>
        <Typography sx={{ fontWeight: 700 }}>Задачи регистратуры</Typography>
        {admin && <Button size="small" startIcon={<AddRounded />} onClick={() => { setTaskError(''); setTaskDialog(true) }}>Задача</Button>}
      </Box>
      {tasksError && <Alert severity="warning" sx={{ mt: 1 }}>{tasksError}</Alert>}
      <TaskList tasks={tasks} today={today} emptyText="Задач нет" staffName={registry.staffName} canClose={registry.canClose} onToggle={(task) => void registry.toggleTask(task)} />
      {editing && <RegistryDialog initial={{ referralSource: patient.referralSource ?? null, registryNote: patient.registryNote ?? null, telegramConnected: Boolean(patient.telegramConnected) }} error={editError} onClose={() => setEditing(false)} onSave={async (input) => {
        const error = await onSave(input)
        if (error) setEditError(error)
        else setEditing(false)
      }} />}
      {taskDialog && <TaskDialog patients={[patient]} patientId={patient.id} staff={registry.staffList} defaultAssigneeId={selfId} today={today} error={taskError} onClose={() => setTaskDialog(false)} onSave={async (draft) => {
        const error = await registry.addTask(draft)
        if (error) setTaskError(error)
        else setTaskDialog(false)
      }} />}
    </>
  )
}
