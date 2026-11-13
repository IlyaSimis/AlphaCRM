import type { StaffRole } from '../../api/types'
import type { Role } from '../../shared/roles'

/** 3–5 полезных показателей под роль; сервер дополнительно ограничивает их разрешениями. */
export function dashboardCodes(role: Role): string[] {
  const roles = Array.isArray(role) ? role : [role]
  const has = (code: StaffRole) => roles.includes(code)
  if (has('SUPPORT') && roles.length === 1) return ['delivery.failed', 'events.backlog']
  if (has('OWNER')) return ['revenue.month', 'schedule.load', 'appointments.month.noShow', 'stock.shortage', 'stock.expiring']
  if (has('MANAGER')) return ['revenue.month', 'schedule.load', 'appointments.month.noShow', 'charges.unpaid', 'salary.month']
  if (has('DOCTOR')) return ['appointments.month', 'salary.month', 'appointments.month.noShow', 'appointments.today', 'documents.unsigned']
  if (has('NURSE') || has('SENIOR_NURSE')) return ['stock.shortage', 'stock.expiring', 'appointments.today', 'salary.month']
  if (has('CASHIER') && roles.length === 1) return ['charges.unpaid', 'charges.awaitingAmount', 'salary.month']
  return ['appointments.month', 'salary.month', 'appointments.today', 'charges.unpaid', 'tasks.due']
}
