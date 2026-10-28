import { describe, expect, it } from 'vitest'
import type { ScheduleItem, TaskItem } from '../../api/types'
import { overdueTasks, todayBlocks, unpaidRows, upcomingAppointments } from './today'

const task = (id: string, dueDate: string, status: TaskItem['status'] = 'OPEN'): TaskItem => ({ id, patientId: null, title: id, dueDate, assigneeId: 'a', status })
const visit = (id: string, date: string, time: string, statusCode: ScheduleItem['statusCode']): ScheduleItem => ({ id, date, time, duration: '30 мин', minutes: 30, name: id, type: 'Приём', status: statusCode, statusCode, tone: 'blue', practitionerId: 'p', patientId: id })

describe('today', () => {
  it('overdue tasks are open ones due strictly before today', () => {
    const tasks = [task('today', '2026-10-20'), task('yesterday', '2026-10-19'), task('week-ago', '2026-10-13'), task('done', '2026-10-01', 'DONE')]
    expect(overdueTasks(tasks, '2026-10-20').map((item) => item.id)).toEqual(['week-ago', 'yesterday'])
  })

  it('appointments for today and tomorrow skip cancellations and no-shows', () => {
    const items = [
      visit('tomorrow', '2026-10-21', '09:00', 'BOOKED'),
      visit('later', '2026-10-20', '12:00', 'CONFIRMED'),
      visit('first', '2026-10-20', '09:00', 'ARRIVED'),
      visit('cancelled', '2026-10-20', '10:00', 'CANCELLED'),
      visit('no-show', '2026-10-21', '11:00', 'NO_SHOW'),
      visit('after', '2026-10-22', '09:00', 'BOOKED'),
      visit('before', '2026-10-19', '09:00', 'COMPLETED'),
    ]
    expect(upcomingAppointments(items, '2026-10-20', '2026-10-21').map((item) => item.id)).toEqual(['first', 'later', 'tomorrow'])
  })

  it('unpaid means completed without a charge, with a pending one or waiting for an open-price amount', () => {
    const appointment = visit('done', '2026-10-15', '09:00', 'COMPLETED')
    const rows = [
      { appointment, state: 'NO_CHARGE' as const },
      { appointment, state: 'AWAITING_AMOUNT' as const },
      { appointment, state: 'PENDING' as const },
      { appointment, state: 'PAID' as const },
    ]
    expect(unpaidRows(rows).map((row) => row.state)).toEqual(['NO_CHARGE', 'AWAITING_AMOUNT', 'PENDING'])
  })

  it('each block only for roles with access to its data', () => {
    expect(todayBlocks('ADMINISTRATOR')).toEqual({ appointments: true, tasks: true, unpaid: true, failed: true })
    expect(todayBlocks('CASHIER')).toEqual({ appointments: true, tasks: true, unpaid: true, failed: false })
    // Менеджер видит счета на чтение (раздел C: finance — R).
    expect(todayBlocks('MANAGER')).toEqual({ appointments: true, tasks: true, unpaid: true, failed: false })
    expect(todayBlocks('DOCTOR')).toEqual({ appointments: false, tasks: false, unpaid: false, failed: false })
    expect(todayBlocks('NURSE')).toEqual({ appointments: true, tasks: false, unpaid: false, failed: false })
    // Техподдержка: ошибки доставки без текста и адресата, без пациентов.
    expect(todayBlocks('SUPPORT')).toEqual({ appointments: false, tasks: false, unpaid: false, failed: true })
  })
})
