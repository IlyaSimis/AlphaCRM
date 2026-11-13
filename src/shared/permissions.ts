import type { StaffItem, StaffRole } from '../api/types'

/**
 * Разрешения ролей — та же матрица, что на сервере (RolePermissions.java, раздел C целевой
 * архитектуры). Интерфейс по ним только прячет кнопки и страницы; решение принимает сервер. На живом
 * сервере набор приходит в /auth/me (permissions), в песочнице берётся отсюда.
 */
export type Permission =
  | 'identity.staff.read' | 'identity.staff.manage' | 'identity.role.assign-admin' | 'identity.role.assign-privileged'
  | 'identity.staff.reset-password' | 'identity.staff.reset-password-platform'
  | 'identity.hr.manage' | 'identity.hr.read-own'
  | 'schedule.appointment.read' | 'schedule.appointment.write' | 'schedule.appointment.write-own'
  | 'schedule.practitioner.read' | 'schedule.practitioner.manage' | 'schedule.service.read' | 'schedule.service.manage'
  | 'schedule.registry-task.read' | 'schedule.registry-task.manage' | 'schedule.waitlist.read' | 'schedule.waitlist.manage'
  | 'clinical.patient.read' | 'clinical.patient.read-care' | 'clinical.patient.contacts-read' | 'clinical.patient.write'
  | 'clinical.note.write' | 'clinical.signed-document.read' | 'clinical.signed-document.read-own'
  | 'clinical.record.break-glass' | 'clinical.break-glass.review'
  | 'clinical.patient-file.write' | 'clinical.patient-file.write-care' | 'clinical.patient-file.read' | 'clinical.patient-file.read-signed'
  | 'clinical.contract.use'
  | 'finance.invoice.read' | 'finance.invoice.write' | 'finance.invoice.set-amount' | 'finance.invoice.void' | 'finance.payment.refund' | 'finance.revenue.read'
  | 'inventory.item.read' | 'inventory.item.write' | 'inventory.document.write' | 'inventory.document.allow-negative'
  | 'inventory.stock.read' | 'inventory.marking.read' | 'inventory.marking.manage'
  | 'drive.file.use' | 'drive.clinic.admin' | 'workspace.item.use'
  | 'assistant.navigation.use'
  | 'insights.dashboard.read' | 'insights.analytics.read' | 'insights.analytics.read-own'
  | 'audit.event.read' | 'audit.event.read-platform'
  | 'communications.delivery.read' | 'communications.delivery.retry'
  | 'integration.configuration.read' | 'integration.configuration.manage'

const OPERATIONS: Permission[] = [
  'identity.staff.read', 'identity.staff.manage', 'identity.role.assign-admin', 'identity.staff.reset-password',
  'schedule.appointment.read', 'schedule.appointment.write', 'schedule.practitioner.read', 'schedule.practitioner.manage',
  'schedule.service.read', 'schedule.service.manage', 'schedule.registry-task.read', 'schedule.registry-task.manage',
  'schedule.waitlist.read', 'schedule.waitlist.manage',
  'clinical.patient.read', 'clinical.patient.contacts-read', 'clinical.patient.write', 'clinical.patient-file.write', 'clinical.patient-file.read', 'clinical.contract.use',
  'finance.invoice.read', 'finance.invoice.write', 'finance.invoice.set-amount', 'finance.invoice.void', 'finance.payment.refund', 'finance.revenue.read',
  'inventory.item.read', 'inventory.item.write', 'inventory.document.write', 'inventory.document.allow-negative',
  'inventory.stock.read', 'inventory.marking.read', 'inventory.marking.manage',
  'drive.file.use', 'workspace.item.use', 'assistant.navigation.use', 'drive.clinic.admin', 'insights.dashboard.read', 'insights.analytics.read', 'audit.event.read',
  'communications.delivery.read', 'communications.delivery.retry', 'identity.hr.read-own',
  'integration.configuration.read', 'integration.configuration.manage',
]

export const rolePermissions: Record<StaffRole, Permission[]> = {
  OWNER: [...OPERATIONS, 'identity.role.assign-privileged', 'clinical.record.break-glass', 'clinical.break-glass.review', 'identity.hr.manage'],
  SENIOR_ADMINISTRATOR: [...OPERATIONS, 'clinical.break-glass.review'],
  ADMINISTRATOR: OPERATIONS,
  MANAGER: [
    'identity.staff.read', 'identity.staff.manage', 'identity.staff.reset-password',
    'schedule.appointment.read', 'schedule.appointment.write', 'schedule.practitioner.read', 'schedule.practitioner.manage',
    'schedule.service.read', 'schedule.registry-task.read', 'schedule.waitlist.read',
    'clinical.patient.read', 'clinical.patient.contacts-read', 'clinical.patient.write', 'clinical.contract.use',
    'clinical.signed-document.read', 'clinical.patient-file.read-signed',
    'finance.invoice.read', 'finance.revenue.read',
    'inventory.item.read', 'inventory.item.write', 'inventory.document.write', 'inventory.stock.read', 'inventory.marking.read', 'inventory.marking.manage',
    'drive.file.use', 'workspace.item.use', 'assistant.navigation.use', 'insights.dashboard.read', 'insights.analytics.read', 'audit.event.read', 'identity.hr.read-own',
  ],
  DOCTOR: [
    'schedule.appointment.read', 'schedule.appointment.write-own', 'schedule.practitioner.read', 'schedule.service.read',
    'clinical.patient.read-care', 'clinical.patient.contacts-read', 'clinical.note.write', 'clinical.signed-document.read-own',
    'clinical.patient-file.write-care', 'clinical.contract.use',
    'drive.file.use', 'workspace.item.use', 'assistant.navigation.use', 'insights.dashboard.read', 'insights.analytics.read-own', 'identity.hr.read-own',
  ],
  NURSE: [
    'schedule.appointment.read', 'schedule.appointment.write', 'schedule.practitioner.read', 'schedule.service.read',
    'clinical.patient.read', 'clinical.patient.contacts-read', 'clinical.contract.use',
    'inventory.item.read', 'inventory.item.write', 'inventory.document.write', 'inventory.stock.read', 'inventory.marking.read',
    'drive.file.use', 'workspace.item.use', 'assistant.navigation.use', 'insights.dashboard.read', 'identity.hr.read-own',
  ],
  SENIOR_NURSE: [
    'schedule.appointment.read', 'schedule.appointment.write', 'schedule.practitioner.read', 'schedule.service.read',
    'clinical.patient.read', 'clinical.patient.contacts-read', 'clinical.contract.use',
    'inventory.item.read', 'inventory.item.write', 'inventory.document.write', 'inventory.stock.read', 'inventory.marking.read',
    'drive.file.use', 'workspace.item.use', 'assistant.navigation.use', 'insights.dashboard.read', 'identity.hr.read-own',
  ],
  CASHIER: [
    'schedule.appointment.read', 'schedule.practitioner.read', 'schedule.service.read', 'clinical.patient.read', 'clinical.contract.use',
    'schedule.registry-task.read', 'finance.invoice.read', 'finance.invoice.write', 'finance.invoice.set-amount',
    'drive.file.use', 'workspace.item.use', 'assistant.navigation.use', 'insights.dashboard.read', 'identity.hr.read-own',
  ],
  SUPPORT: [
    'identity.staff.read', 'identity.staff.reset-password-platform', 'schedule.appointment.read', 'schedule.practitioner.read',
    'schedule.service.read', 'clinical.contract.use',
    'drive.file.use', 'workspace.item.use', 'assistant.navigation.use', 'drive.clinic.admin', 'insights.dashboard.read', 'audit.event.read-platform',
    'communications.delivery.read', 'communications.delivery.retry', 'identity.hr.read-own',
  ],
}

/** Одна роль или несколько: у сотрудника с несколькими ролями права складываются. */
export type RoleSet = StaffRole | readonly StaffRole[]

const asList = (role: RoleSet): readonly StaffRole[] => (typeof role === 'string' ? [role] : role)

/** Роли сотрудника для проверок прав: одна — кодом, несколько — тем же массивом (без копии). */
export function rolesOf(staff: Pick<StaffItem, 'role' | 'roles'>): RoleSet {
  return staff.roles && staff.roles.length > 1 ? staff.roles : staff.role
}

/** Разрешения сотрудника: с сервера, если пришли, иначе по ролям. */
export function permissionsOf(staff: Pick<StaffItem, 'role' | 'roles' | 'permissions'>): Permission[] {
  if (staff.permissions && staff.permissions.length > 0) return staff.permissions as Permission[]
  return [...new Set(asList(rolesOf(staff)).flatMap((role) => rolePermissions[role] ?? []))]
}

/** Есть ли у роли (у одной из ролей) хотя бы одно из разрешений. */
export function can(role: RoleSet, ...permissions: Permission[]) {
  return asList(role).some((item) => {
    const granted = rolePermissions[item] ?? []
    return permissions.some((permission) => granted.includes(permission))
  })
}

/** Есть ли среди ролей эта. */
export const hasRole = (role: RoleSet, target: StaffRole) => asList(role).includes(target)

/** Роли, которые сотрудник может назначить (и обслуживать): не выше своих прав. */
export function assignableRoles(role: RoleSet): StaffRole[] {
  const all: StaffRole[] = ['OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR', 'MANAGER', 'DOCTOR', 'NURSE', 'SENIOR_NURSE', 'CASHIER', 'SUPPORT']
  return all.filter((target) => {
    if (target === 'OWNER' || target === 'SENIOR_ADMINISTRATOR') return can(role, 'identity.role.assign-privileged')
    if (target === 'ADMINISTRATOR' || target === 'SUPPORT') return can(role, 'identity.role.assign-admin')
    return can(role, 'identity.staff.manage')
  })
}
