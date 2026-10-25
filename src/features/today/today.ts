import type { ScheduleItem, TaskItem } from '../../api/types'
import type { Role } from '../../shared/roles'
import { canManageClinic, canSeeCashier, canSeeRegistry } from '../../shared/roles'
import type { CashierRow } from '../cashier/cashier'

/** Сколько дней назад касса и «Сегодня» ищут завершённые неоплаченные приёмы. */
export const UNPAID_PERIOD_DAYS = 90

/** Какие блоки «Сегодня» видит роль: каждый — только тем, у кого есть доступ к этим данным. */
export function todayBlocks(role: Role) {
  return {
    appointments: role !== 'DOCTOR',
    tasks: canSeeRegistry(role),
    unpaid: canSeeCashier(role),
    failed: canManageClinic(role),
  }
}

export type TodayBlocks = ReturnType<typeof todayBlocks>

/** Записи на сегодня и завтра без отмен и неявок, по времени. */
export function upcomingAppointments(items: ScheduleItem[], today: string, tomorrow: string) {
  return items
    .filter((item) => item.date >= today && item.date <= tomorrow && item.statusCode !== 'CANCELLED' && item.statusCode !== 'NO_SHOW')
    .sort((left, right) => left.date.localeCompare(right.date) || left.time.localeCompare(right.time))
}

/**
 * Просроченные: открытые задачи со сроком строго раньше сегодняшнего дня. Сервер отдаёт задачи
 * со сроком до сегодня включительно — сегодняшние здесь отбрасываются.
 */
export function overdueTasks(tasks: TaskItem[], today: string) {
  return tasks
    .filter((task) => task.status === 'OPEN' && task.dueDate < today)
    .sort((left, right) => left.dueDate.localeCompare(right.dueDate))
}

/** Завершённые приёмы без счёта или с неоплаченным счётом. */
export function unpaidRows(rows: CashierRow[]) {
  return rows.filter((row) => row.state === 'NO_CHARGE' || row.state === 'PENDING')
}

/** Сумма строки: счёт, если выставлен, иначе снимок цены записи. */
export function rowAmount(row: CashierRow) {
  return row.charge?.amountMinor ?? row.appointment.amountMinor ?? null
}
