import { useCallback, useEffect, useState } from 'react'
import { addToWaitlist, bookFromWaitlist, cancelWaitlist, createTask, loadPatientTasks, loadTasksDue, loadWaitlist, setTaskDone } from '../../api/registry'
import { loadStaff } from '../../api/staff'
import type { StaffItem, TaskItem, WaitlistItem } from '../../api/types'
import { demoTasks, demoWaitlist, sandboxStaff as initialSandboxStaff } from '../../app/demoData'
import { localToday } from '../../shared/dates'
import { canManageClinic, canSeeRegistry, canSeeWaitlist } from '../../shared/roles'
import { useSandboxState } from '../../shared/sandbox'
import { dueTasks, matchingWaitlist } from './registry'

export type TaskDraft = { title: string; dueDate: string; assigneeId: string; patientId: string | null }
export type WaitlistDraft = { patientId: string; practitionerId: string; serviceId: string; wishFrom: string; wishTo: string }

/**
 * Задачи регистратуры и лист ожидания. Это внутренние напоминания сотрудникам:
 * пациенту ничего не отправляется. На сервере — API регистратуры, в песочнице — память вкладки.
 */
export function useRegistry(staff: StaffItem, live: boolean, apiOnline: boolean, notify: (message: string) => void) {
  const role = staff.role
  const sandbox = !live
  const [tasks, setTasks] = useSandboxState<TaskItem[]>('tasks', live ? [] : demoTasks, sandbox)
  const [waitlist, setWaitlist] = useSandboxState<WaitlistItem[]>('waitlist', live ? [] : demoWaitlist, sandbox)
  const [sandboxStaff] = useSandboxState<StaffItem[]>('staff', initialSandboxStaff, sandbox)
  const [remoteStaff, setRemoteStaff] = useState<StaffItem[] | null>(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const today = localToday()
  const bump = () => setRevision((value) => value + 1)

  useEffect(() => {
    if (!apiOnline) return
    let cancelled = false
    if (canSeeRegistry(role)) {
      loadTasksDue(today)
        .then((items) => { if (!cancelled) { setTasks(items); setError('') } })
        .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    }
    if (canSeeWaitlist(role)) {
      loadWaitlist()
        .then((items) => { if (!cancelled) setWaitlist(items) })
        .catch((reason: Error) => { if (!cancelled) setError(reason.message) })
    }
    if (canManageClinic(role)) {
      loadStaff()
        .then((items) => { if (!cancelled) setRemoteStaff(items) })
        .catch(() => { /* исполнителей подставим из известных учётных записей */ })
    }
    return () => { cancelled = true }
  }, [apiOnline, role, today, revision])

  /** Сотрудники для выбора исполнителя и подписи задач. На сервере — только его учётные записи. */
  const staffList = live ? remoteStaff ?? [staff] : sandboxStaff
  const staffName = (id: string) => staffList.find((item) => item.id === id)?.fullName ?? 'Сотрудник'

  /** Все задачи пациента: в карточке видны и выполненные. */
  const patientTasks = useCallback(async (patientId: string): Promise<TaskItem[]> => {
    if (apiOnline) return loadPatientTasks(patientId)
    return tasks.filter((task) => task.patientId === patientId).sort((left, right) => left.dueDate.localeCompare(right.dueDate))
  }, [apiOnline, tasks])

  const addTask = async (draft: TaskDraft): Promise<string | null> => {
    if (apiOnline) {
      try {
        await createTask({ ...draft, title: draft.title.trim() })
        notify('Задача создана')
        bump()
        return null
      } catch (reason) {
        return (reason as Error).message
      }
    }
    setTasks((items) => [...items, { id: crypto.randomUUID(), ...draft, title: draft.title.trim(), status: 'OPEN' }])
    notify('Демо-режим: задача видна только до обновления страницы')
    bump()
    return null
  }

  /** Закрыть задачу может её исполнитель или администратор — как на сервере. */
  const canClose = (task: TaskItem) => role === 'ADMINISTRATOR' || task.assigneeId === staff.id

  const toggleTask = async (task: TaskItem) => {
    const done = task.status === 'OPEN'
    if (apiOnline) {
      try {
        await setTaskDone(task.id, done)
        notify(done ? 'Задача выполнена' : 'Задача снова открыта')
        bump()
      } catch (reason) {
        notify((reason as Error).message)
      }
      return
    }
    setTasks((items) => items.map((item) => item.id === task.id ? { ...item, status: done ? 'DONE' : 'OPEN', doneAt: done ? new Date().toISOString() : null } : item))
    notify(done ? 'Демо-режим: задача выполнена' : 'Демо-режим: задача снова открыта')
    bump()
  }

  const addWaiting = async (draft: WaitlistDraft): Promise<string | null> => {
    if (draft.wishTo < draft.wishFrom) return 'Конец периода раньше начала'
    if (apiOnline) {
      try {
        await addToWaitlist(draft)
        notify('Пациент добавлен в лист ожидания')
        bump()
        return null
      } catch (reason) {
        return (reason as Error).message
      }
    }
    setWaitlist((items) => [...items, { id: crypto.randomUUID(), ...draft, status: 'ACTIVE' }])
    notify('Демо-режим: лист ожидания виден только до обновления страницы')
    return null
  }

  const removeWaiting = async (entry: WaitlistItem) => {
    if (apiOnline) {
      try {
        await cancelWaitlist(entry.id)
        notify('Пациент убран из листа ожидания')
        bump()
      } catch (reason) {
        notify((reason as Error).message)
      }
      return
    }
    setWaitlist((items) => items.map((item) => item.id === entry.id ? { ...item, status: 'CANCELLED' } : item))
    notify('Демо-режим: пациент убран из листа ожидания')
  }

  /** Запись уже создана обычным POST /appointments — ожидание только закрывается ссылкой на неё. */
  const markBooked = async (entry: WaitlistItem, appointmentId: string) => {
    if (apiOnline) {
      try {
        await bookFromWaitlist(entry.id, appointmentId)
        bump()
      } catch (reason) {
        notify((reason as Error).message)
      }
      return
    }
    setWaitlist((items) => items.map((item) => item.id === entry.id ? { ...item, status: 'BOOKED', appointmentId } : item))
  }

  return {
    error,
    revision,
    todayTasks: dueTasks(tasks, today),
    waitlist: waitlist.filter((entry) => entry.status === 'ACTIVE'),
    matches: (practitionerId: string, date: string) => matchingWaitlist(waitlist, practitionerId, date),
    staffList: staffList.filter((item) => item.active),
    staffName,
    patientTasks,
    addTask,
    canClose,
    toggleTask,
    addWaiting,
    removeWaiting,
    markBooked,
  }
}

export type Registry = ReturnType<typeof useRegistry>
