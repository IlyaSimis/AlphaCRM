import { describe, expect, it } from 'vitest'
import type { WorkspaceItem } from '../../api/types'
import { blankWorkspaceItem, emptyWorkspaceFilter, filterWorkspace, isWorkspaceOverdue, newWorkspaceId } from './workspace'

function item(patch: Partial<WorkspaceItem> = {}): WorkspaceItem {
  return { ...blankWorkspaceItem(), id: newWorkspaceId(), title: 'Задача', createdAt: '2026-10-01T09:00:00Z', updatedAt: '2026-10-20T09:00:00Z', completedAt: null, archivedAt: null, ...patch }
}

describe('personal workspace', () => {
  it('searches custom fields, tags and checklist text without a fixed schema', () => {
    const match = item({ fields: [{ name: 'Мой параметр', value: 'особое значение' }] })
    const other = item({ title: 'Другое' })
    expect(filterWorkspace([match, other], { ...emptyWorkspaceFilter, query: 'особое' }, '2026-10-20')).toEqual([match])
    expect(filterWorkspace([match, other], { ...emptyWorkspaceFilter, query: 'Мой параметр' }, '2026-10-20')).toEqual([match])
  })

  it('filters by free type, status, tag and due period', () => {
    const match = item({ type: 'Идея', status: 'Обдумать', tags: ['рост'], dueAt: '2026-10-22T09:00:00Z' })
    const overdue = item({ dueAt: '2026-10-19T09:00:00Z' })
    const noDate = item()
    expect(filterWorkspace([match, overdue, noDate], { ...emptyWorkspaceFilter, type: 'Идея', status: 'Обдумать', tag: 'рост', due: 'week' }, '2026-10-20')).toEqual([match])
    expect(filterWorkspace([match, overdue, noDate], { ...emptyWorkspaceFilter, due: 'overdue' }, '2026-10-20')).toEqual([overdue])
    expect(filterWorkspace([match, overdue, noDate], { ...emptyWorkspaceFilter, due: 'none' }, '2026-10-20')).toEqual([noDate])
    expect(isWorkspaceOverdue(overdue, '2026-10-20')).toBe(true)
    expect(isWorkspaceOverdue({ ...overdue, completed: true }, '2026-10-20')).toBe(false)
  })

  it('filters only tasks assigned to the current user and combines with tags', () => {
    const assigned = item({ title: 'Общая задача', type: 'Задача', assigneeId: 'staff-test', tags: ['отчёт'] })
    const otherAssignee = item({ title: 'Чужая задача', type: 'Задача', assigneeId: 'staff-doctor', tags: ['отчёт'] })
    const ownNote = item({ title: 'Личная заметка', type: 'Заметка', assigneeId: 'staff-test', tags: ['отчёт'] })
    const records = [assigned, otherAssignee, ownNote]
    expect(filterWorkspace(records, { ...emptyWorkspaceFilter, assigneeId: 'staff-test' }, '2026-10-20')).toEqual([assigned])
    expect(filterWorkspace(records, { ...emptyWorkspaceFilter, assigneeId: 'staff-test', tag: 'отчёт' }, '2026-10-20')).toEqual([assigned])
    expect(filterWorkspace(records, { ...emptyWorkspaceFilter, assigneeId: 'staff-test', tag: 'другое' }, '2026-10-20')).toEqual([])
  })

  it('sorts unfinished by date and keeps completed at the end', () => {
    const later = item({ title: 'Позже', dueAt: '2026-10-22T09:00:00Z' })
    const earlier = item({ title: 'Раньше', dueAt: '2026-10-21T09:00:00Z' })
    const done = item({ title: 'Готово', completed: true, dueAt: '2026-10-20T09:00:00Z' })
    expect(filterWorkspace([later, done, earlier], emptyWorkspaceFilter, '2026-10-20').map((entry) => entry.title)).toEqual(['Раньше', 'Позже', 'Готово'])
  })
})
