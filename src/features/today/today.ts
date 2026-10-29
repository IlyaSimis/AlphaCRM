import type { TaskItem } from '../../api/types'
import type { Role } from '../../shared/roles'
import { can } from '../../shared/permissions'
import { canEditPatients, canSeeCashier, canSeeRegistry, canWriteAppointments } from '../../shared/roles'

/** На главной остаются просроченные задачи только для ролей с доступом к регистратуре. */
export function todayBlocks(role: Role) {
  return { tasks: canSeeRegistry(role) }
}

export type TodayBlocks = ReturnType<typeof todayBlocks>

/** Быстрые действия доступны только там, где роль может довести их до конца. */
export function todayActions(role: Role) {
  return {
    booking: canWriteAppointments(role) && can(role, 'clinical.patient.read'),
    patient: canEditPatients(role),
    cashier: canSeeCashier(role),
  }
}

/** Просроченные: открытые задачи со сроком строго раньше сегодняшнего дня. */
export function overdueTasks(tasks: TaskItem[], today: string) {
  return tasks
    .filter((task) => task.status === 'OPEN' && task.dueDate < today)
    .sort((left, right) => left.dueDate.localeCompare(right.dueDate))
}
