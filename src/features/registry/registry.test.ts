import { describe, expect, it } from 'vitest'
import type { PatientItem, TaskItem, WaitlistItem } from '../../api/types'
import { dueTasks, matchingWaitlist, periodLabel, phoneKey, phoneSharedWith, referralLabel } from './registry'

const task = (id: string, dueDate: string, status: TaskItem['status'] = 'OPEN'): TaskItem => ({ id, patientId: 'p', title: 'Call back', dueDate, assigneeId: 'a', status })

const entry = (id: string, status: WaitlistItem['status'] = 'ACTIVE'): WaitlistItem => ({ id, patientId: 'p', practitionerId: 'doc-1', serviceId: 's', wishFrom: '2026-10-19', wishTo: '2026-10-23', status })

describe('registry', () => {
  it('today means open tasks due today and overdue', () => {
    const tasks = [task('later', '2026-10-21'), task('today', '2026-10-20'), task('late', '2026-10-18'), task('done', '2026-10-19', 'DONE')]
    expect(dueTasks(tasks, '2026-10-20').map((item) => item.id)).toEqual(['late', 'today'])
  })

  it('a free slot matches patients waiting for this doctor in the wished period', () => {
    const entries = [entry('w1'), entry('w2', 'BOOKED'), { ...entry('w3'), practitionerId: 'doc-2' }]
    expect(matchingWaitlist(entries, 'doc-1', '2026-10-20').map((item) => item.id)).toEqual(['w1'])
    expect(matchingWaitlist(entries, 'doc-1', '2026-10-23').map((item) => item.id)).toEqual(['w1'])
    expect(matchingWaitlist(entries, 'doc-1', '2026-10-24')).toEqual([])
  })

  it('compares phones by digits; 8 and +7 are the same number', () => {
    expect(phoneKey('8 (916) 245-18-72')).toBe(phoneKey('+7 916 245 18 72'))
    const patients = [{ id: 'a', name: 'Анна', phone: '+7 916 245-18-72' }, { id: 'b', name: 'Борис', phone: '89162451872' }, { id: 'c', name: 'Вера', phone: '+7 903 000-00-00' }] as PatientItem[]
    expect(phoneSharedWith('+7 916 245-18-72', patients, 'a')).toEqual([{ id: 'b', fullName: 'Борис' }])
    expect(phoneSharedWith('—', patients)).toEqual([])
    // Второй телефон сравнивается с обоими телефонами других карточек.
    const withSecond = [...patients, { id: 'd', name: 'Галина', phone: '+7 900 000-00-00', secondPhone: '8 916 245 18 72' }] as PatientItem[]
    expect(phoneSharedWith(['+7 999 000-00-00', '+7 916 245-18-72'], withSecond, 'a').map((item) => item.fullName)).toEqual(['Борис', 'Галина'])
    expect(phoneSharedWith(['', null], withSecond)).toEqual([])
  })

  it('labels the patient referral source and waiting period', () => {
    expect(referralLabel('WEBSITE')).toBe('Сайт клиники')
    expect(referralLabel(null)).toBe('не указано')
    expect(periodLabel('2026-10-19', '2026-10-30')).toBe('19.10.2026 – 30.10.2026')
  })
})
