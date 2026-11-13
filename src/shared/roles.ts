import type { StaffItem, StaffRole } from '../api/types'
import { can, type RoleSet } from './permissions'
import { initialsOf } from './text'

/**
 * Роль в интерфейсе — тот же машинный код, что у учётной записи на сервере. У сотрудника с
 * несколькими ролями — массив кодов: права складываются.
 */
export type Role = RoleSet

/** Название роли на экране. */
export const roleLabels: Record<StaffRole, string> = {
  OWNER: 'Владелец клиники',
  SENIOR_ADMINISTRATOR: 'Старший администратор',
  ADMINISTRATOR: 'Администратор',
  MANAGER: 'Менеджер',
  DOCTOR: 'Врач',
  NURSE: 'Медперсонал',
  SENIOR_NURSE: 'Старший медперсонал',
  CASHIER: 'Кассир',
  SUPPORT: 'Техподдержка',
}

export const staffRoles: StaffRole[] = ['OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR', 'MANAGER', 'DOCTOR', 'NURSE', 'SENIOR_NURSE', 'CASHIER', 'SUPPORT']

/** Роли, которые могут быть постоянным администратором пациента. */
export const administratorRoles: StaffRole[] = ['OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR']

/** Названия ролей через запятую: «Менеджер, Кассир». */
export function roleText(role: Role) {
  const list: readonly StaffRole[] = typeof role === 'string' ? [role] : role
  return list.map((item) => roleLabels[item] ?? item).join(', ')
}

/** Роли сотрудника по старшинству (как на экране «Сотрудники»). */
export function staffRolesOf(staff: Pick<StaffItem, 'role' | 'roles'>): StaffRole[] {
  const list = staff.roles && staff.roles.length > 0 ? staff.roles : [staff.role]
  return staffRoles.filter((role) => list.includes(role))
}

/** Имя, инициалы и название ролей сотрудника для боковой панели. */
export function profileOf(staff: Pick<StaffItem, 'fullName' | 'role' | 'roles'>) {
  return { name: staff.fullName, initials: initialsOf(staff.fullName), role: roleText(staffRolesOf(staff)) }
}

/** Аналитика клиники — у тех, кому она разрешена (врачу — только свои приёмы на дашборде). */
export const canSeeAnalytics = (role: Role) => can(role, 'insights.analytics.read')
/** Журнал: полный — администраторам и менеджеру, без клинических объектов — техподдержке. */
export const canSeeAudit = (role: Role) => can(role, 'audit.event.read', 'audit.event.read-platform')
/** Касса: счета видят администраторы, кассир и менеджер (на чтение). */
export const canSeeCashier = (role: Role) => can(role, 'finance.invoice.read')
/** Выставить счёт и принять оплату. */
export const canTakePayments = (role: Role) => can(role, 'finance.invoice.write')
/** Регистратура: задачи видят администраторы, менеджер и кассир; лист ожидания — без кассира. */
export const canSeeRegistry = (role: Role) => can(role, 'schedule.registry-task.read')
export const canSeeWaitlist = (role: Role) => can(role, 'schedule.waitlist.read')
/** Задачи и лист ожидания ведут администраторы. */
export const canManageRegistry = (role: Role) => can(role, 'schedule.registry-task.manage')
/** Архив подписанных документов — врачу (свои подписи) и менеджеру (все). */
export const canSeeSignedDocuments = (role: Role) => can(role, 'clinical.signed-document.read', 'clinical.signed-document.read-own')
/** График, кабинеты и цены: страница «График и цены». */
export const canManageSchedule = (role: Role) => can(role, 'schedule.practitioner.manage', 'schedule.service.manage')
/** Цены, свободная цена и услуги — администраторы и владелец. */
export const canManagePrices = (role: Role) => can(role, 'schedule.service.manage')
/** Сотрудники: список и учётные записи. */
export const canSeeStaff = (role: Role) => can(role, 'identity.staff.read')
export const canManageStaff = (role: Role) => can(role, 'identity.staff.manage')
/** Внешние выводы (outbox). */
export const canSeeOutbox = (role: Role) => can(role, 'communications.delivery.read')
/** Записать, перенести, отменить любую запись (врач — только свои). */
export const canWriteAppointments = (role: Role) => can(role, 'schedule.appointment.write')
/** Регистрация и правка административной карточки, согласия. */
export const canEditPatients = (role: Role) => can(role, 'clinical.patient.write')
/** Клинический текст — только врачу со связью с лечением. */
export const canWriteClinical = (role: Role) => can(role, 'clinical.note.write')
export const canSeeInventory = (role: Role) => can(role, 'inventory.item.read', 'inventory.stock.read')
export const canSeeDrive = (role: Role) => can(role, 'drive.file.use')
/** Пациенты: список и карточки (у врача — по связи с лечением). */
export const canSeePatients = (role: Role) => can(role, 'clinical.patient.read', 'clinical.patient.read-care')

/**
 * Прежнее «администратор клиники»: страница «График и цены» и управление записями. Оставлено для
 * мест, где решение по-прежнему одно на весь администраторский контур.
 */
export const canManageClinic = (role: Role) => can(role, 'schedule.service.manage')
