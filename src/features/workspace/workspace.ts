import type { WorkspaceFilter, WorkspaceItem, WorkspaceItemInput } from '../../api/types'
import { clinicInstant, clinicParts } from '../../shared/clinicTime'
import { localToday, shiftDate } from '../../shared/dates'

export const emptyWorkspaceFilter: WorkspaceFilter = { query: '', type: '', status: '', tag: '', due: 'all', sort: 'due', display: 'list' }

export function blankWorkspaceItem(type = 'Задача'): WorkspaceItemInput {
  return { title: '', type, body: '', status: type === 'Заметка' ? 'Заметки' : 'К выполнению', color: type === 'Заметка' ? 'blue' : 'green', completed: false, dueAt: null, remindAt: null, patientId: null, appointmentId: null, tags: [], fields: [], checklist: [] }
}

export function inputOf(item: WorkspaceItem): WorkspaceItemInput {
  const { title, type, body, status, color, completed, dueAt, remindAt, patientId, appointmentId, tags, fields, checklist } = item
  return { title, type, body, status, color, completed, dueAt, remindAt, patientId, appointmentId, tags, fields, checklist }
}

export function filterWorkspace(items: WorkspaceItem[], filter: WorkspaceFilter, today = localToday()): WorkspaceItem[] {
  const query = filter.query.trim().toLocaleLowerCase('ru-RU')
  const nextWeek = shiftDate(today, 7)
  return items.filter((item) => {
    if (query) {
      const haystack = [item.title, item.body, item.type, item.status, ...item.tags, ...item.fields.flatMap((field) => [field.name, field.value]), ...item.checklist.map((entry) => entry.text)].join('\n').toLocaleLowerCase('ru-RU')
      if (!haystack.includes(query)) return false
    }
    if (filter.type && item.type !== filter.type) return false
    if (filter.status && item.status !== filter.status) return false
    if (filter.tag && !item.tags.includes(filter.tag)) return false
    const day = item.dueAt ? clinicParts(item.dueAt).date : null
    if (filter.due === 'today' && day !== today) return false
    if (filter.due === 'overdue' && (!day || day >= today || item.completed)) return false
    if (filter.due === 'week' && (!day || day < today || day > nextWeek)) return false
    if (filter.due === 'none' && day) return false
    return true
  }).sort((left, right) => {
    if (left.completed !== right.completed) return Number(left.completed) - Number(right.completed)
    if (filter.sort === 'title') return left.title.localeCompare(right.title, 'ru-RU')
    if (filter.sort === 'updated') return right.updatedAt.localeCompare(left.updatedAt)
    if (filter.sort === 'created') return right.createdAt.localeCompare(left.createdAt)
    const leftDay = left.dueAt ? clinicParts(left.dueAt).date : '9999-12-31'
    const rightDay = right.dueAt ? clinicParts(right.dueAt).date : '9999-12-31'
    return leftDay.localeCompare(rightDay) || right.updatedAt.localeCompare(left.updatedAt)
  })
}

export function workspaceDateInput(instant: string | null) {
  if (!instant) return ''
  const parts = clinicParts(instant)
  return `${parts.date}T${parts.time}`
}

export function workspaceInstant(input: string) {
  if (!input) return null
  const [date, time] = input.split('T')
  const value = clinicInstant(date, time)
  return Number.isNaN(value.getTime()) ? null : value.toISOString()
}

export function isWorkspaceOverdue(item: WorkspaceItem, today = localToday()) {
  return !item.completed && Boolean(item.dueAt && clinicParts(item.dueAt!).date < today)
}

export function isWorkspaceReminderDue(item: WorkspaceItem, now = new Date()) {
  return !item.completed && !item.archivedAt && Boolean(item.remindAt && new Date(item.remindAt!).getTime() <= now.getTime())
}

export function newWorkspaceId() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
