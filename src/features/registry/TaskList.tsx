import { Box, Button, Chip, Typography } from '@mui/material'
import type { TaskItem } from '../../api/types'

type TaskListProps = {
  tasks: TaskItem[]
  today: string
  emptyText: string
  /** Подпись пациента — на странице регистратуры; в карточке не нужна. */
  patientName?: (id: string) => string
  staffName: (id: string) => string
  canClose: (task: TaskItem) => boolean
  onToggle: (task: TaskItem) => void
}

const short = (iso: string) => iso.split('-').reverse().join('.')

export function TaskList({ tasks, today, emptyText, patientName, staffName, canClose, onToggle }: TaskListProps) {
  if (tasks.length === 0) return <Typography sx={{ color: 'text.secondary', mt: 1 }}>{emptyText}</Typography>
  return <>
    {tasks.map((task) => {
      const subject = patientName ? (task.patientId ? patientName(task.patientId) : 'без пациента') : null
      const title = subject ? `${task.title} · ${subject}` : task.title
      const overdue = task.status === 'OPEN' && task.dueDate < today
      return (
        <Box className="history-item" key={task.id}>
          <Box>
            <Typography sx={{ fontWeight: 650, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{title}</Typography>
            <Typography sx={{ fontSize: 13, color: 'text.secondary' }}>срок {short(task.dueDate)} · {staffName(task.assigneeId)}</Typography>
          </Box>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            <Chip size="small" variant="outlined" label={task.status === 'DONE' ? 'Выполнена' : overdue ? 'Просрочена' : 'Открыта'} color={task.status === 'DONE' ? 'success' : overdue ? 'warning' : 'default'} />
            {canClose(task) && <Button size="small" aria-label={`${task.status === 'OPEN' ? 'Выполнено' : 'Вернуть'}: ${title}`} onClick={() => onToggle(task)}>{task.status === 'OPEN' ? 'Выполнено' : 'Вернуть'}</Button>}
          </Box>
        </Box>
      )
    })}
  </>
}
