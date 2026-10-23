import type { StaffItem, StaffRole } from '../api/types'
import { initialsOf } from './text'

/** Роль в интерфейсе — тот же машинный код, что у учётной записи на сервере. */
export type Role = StaffRole

/** Название роли на экране. */
export const roleLabels: Record<StaffRole, string> = { ADMINISTRATOR: 'Администратор', DOCTOR: 'Врач', MANAGER: 'Менеджер', CASHIER: 'Кассир' }

export const staffRoles: StaffRole[] = ['ADMINISTRATOR', 'DOCTOR', 'MANAGER', 'CASHIER']

/** Имя, инициалы и название роли сотрудника для боковой панели. */
export function profileOf(staff: Pick<StaffItem, 'fullName' | 'role'>) {
  return { name: staff.fullName, initials: initialsOf(staff.fullName), role: roleLabels[staff.role] }
}

/** Аналитика и журнал — администратору и менеджеру; касса — кассиру и администратору. */
export const canSeeAnalytics = (role: Role) => role === 'ADMINISTRATOR' || role === 'MANAGER'
export const canSeeCashier = (role: Role) => role === 'CASHIER' || role === 'ADMINISTRATOR'
/** Регистратура: задачи видят все, кроме врача; лист ожидания — администратор и менеджер. */
export const canSeeRegistry = (role: Role) => role !== 'DOCTOR'
export const canSeeWaitlist = (role: Role) => role === 'ADMINISTRATOR' || role === 'MANAGER'
/** Архив подписанных документов — врачу (свои подписи) и менеджеру (все). */
export const canSeeSignedDocuments = (role: Role) => role === 'DOCTOR' || role === 'MANAGER'
/** График, цены и учётные записи меняет только администратор. */
export const canManageClinic = (role: Role) => role === 'ADMINISTRATOR'
