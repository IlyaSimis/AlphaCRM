import { describe, expect, it } from 'vitest'
import type { TaskItem } from '../../api/types'
import { overdueTasks, todayBlocks } from './today'

const task = (id: string, dueDate: string, status: TaskItem['status'] = 'OPEN'): TaskItem => ({ id, patientId: null, title: id, dueDate, assigneeId: 'a', status })

describe('today', () => {
  it('overdue tasks are open ones due strictly before today', () => {
    const tasks = [task('today', '2026-10-20'), task('yesterday', '2026-10-19'), task('week-ago', '2026-10-13'), task('done', '2026-10-01', 'DONE')]
    expect(overdueTasks(tasks, '2026-10-20').map((item) => item.id)).toEqual(['week-ago', 'yesterday'])
  })

  it('overdue tasks stay behind the registry permission', () => {
    expect(todayBlocks('ADMINISTRATOR')).toEqual({ tasks: true })
    expect(todayBlocks('CASHIER')).toEqual({ tasks: true })
    expect(todayBlocks('MANAGER')).toEqual({ tasks: true })
    expect(todayBlocks('DOCTOR')).toEqual({ tasks: false })
    expect(todayBlocks('NURSE')).toEqual({ tasks: false })
    expect(todayBlocks('SUPPORT')).toEqual({ tasks: false })
  })
})
