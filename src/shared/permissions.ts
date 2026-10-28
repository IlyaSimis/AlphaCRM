import type { StaffItem, StaffRole } from '../api/types'

/**
 * Разрешения ролей — та же матрица, что на сервере (RolePermissions.java, раздел C целевой
 * архитектуры). Интерфейс по ним только прячет кнопки и страницы; решение принимает сервер. На живом
 * сервере набор приходит в /auth/me (permissions), в песочнице берётся отсюда.
 */
export type Permission =
  | 'identity.staff.read' | 'identity.staff.manage' | 'identity.role.assign-admin' | 'identity.role.assign-privileged'
  | 'identity.staff.reset-password' | 'identity.staff.reset-password-platform'
  | 'schedule.appointment.read' | 'schedule.appointment.write' | 'schedule.appointment.write-own'
  | 'schedule.practitioner.read' | 'schedule.practitioner.manage' | 'schedule.service.read' | 'schedule.service.manage'
  | 'schedule.registry-task.read' | 'schedule.registry-task.manage' | 'schedule.waitlist.read' | 'schedule.waitlist.manage'
  | 'clinical.patient.read' | 'clinical.patient.read-care' | 'clinical.patient.contacts-read' | 'clinical.patient.write'
  | 'clinical.note.write' | 'clinical.signed-document.read' | 'clinical.signed-document.read-own'
  | 'clinical.record.break-glass' | 'clinical.break-glass.review'
  | 'clinical.patient-file.write' | 'clinical.patient-file.write-care' | 'clinical.patient-file.read' | 'clinical.patient-file.read-signed'
  | 'finance.invoice.read' | 'finance.invoice.write' | 'finance.invoice.set-amount' | 'finance.invoice.void' | 'finance.payment.refund' | 'finance.revenue.read'
  | 'inventory.item.read' | 'inventory.item.write' | 'inventory.document.write' | 'inventory.document.allow-negative'
  | 'inventory.stock.read' | 'inventory.marking.read' | 'inventory.marking.manage'
  | 'drive.file.use' | 'drive.admin'
  | 'insights.dashboard.read' | 'insights.analytics.read' | 'insights.analytics.read-own'
  | 'audit.event.read' | 'audit.event.read-platform'
  | 'communications.delivery.read' | 'communications.delivery.retry'

const OPERATIONS: Permission[] = [
  'identity.staff.read', 'identity.staff.manage', 'identity.role.assign-admin', 'identity.staff.reset-password',
  'schedule.appointment.read', 'schedule.appointment.write', 'schedule.practitioner.read', 'schedule.practitioner.manage',
  'schedule.service.read', 'schedule.service.manage', 'schedule.registry-task.read', 'schedule.registry-task.manage',
  'schedule.waitlist.read', 'schedule.waitlist.manage',
  'clinical.patient.read', 'clinical.patient.contacts-read', 'clinical.patient.write', 'clinical.patient-file.write', 'clinical.patient-file.read',
  'finance.invoice.read', 'finance.invoice.write', 'finance.invoice.set-amount', 'finance.invoice.void', 'finance.payment.refund', 'finance.revenue.read',
  'inventory.item.read', 'inventory.item.write', 'inventory.document.write', 'inventory.document.allow-negative',
  'inventory.stock.read', 'inventory.marking.read', 'inventory.marking.manage',
  'drive.file.use', 'drive.admin', 'insights.dashboard.read', 'insights.analytics.read', 'audit.event.read',
  'communications.delivery.read', 'communications.delivery.retry',
]

export const rolePermissions: Record<StaffRole, Permission[]> = {
  OWNER: [...OPERATIONS, 'identity.role.assign-privileged', 'clinical.record.break-glass', 'clinical.break-glass.review'],
  SENIOR_ADMINISTRATOR: [...OPERATIONS, 'clinical.break-glass.review'],
  ADMINISTRATOR: OPERATIONS,
  MANAGER: [
    'identity.staff.read', 'identity.staff.manage', 'identity.staff.reset-password',
    'schedule.appointment.read', 'schedule.appointment.write', 'schedule.practitioner.read', 'schedule.practitioner.manage',
    'schedule.service.read', 'schedule.registry-task.read', 'schedule.waitlist.read',
    'clinical.patient.read', 'clinical.patient.contacts-read', 'clinical.patient.write',
    'clinical.signed-document.read', 'clinical.patient-file.read-signed',
    'finance.invoice.read', 'finance.revenue.read',
    'inventory.item.read', 'inventory.item.write', 'inventory.document.write', 'inventory.stock.read', 'inventory.marking.read', 'inventory.marking.manage',
    'drive.file.use', 'insights.dashboard.read', 'insights.analytics.read', 'audit.event.read',
  ],
  DOCTOR: [
    'schedule.appointment.read', 'schedule.appointment.write-own', 'schedule.practitioner.read', 'schedule.service.read',
    'clinical.patient.read-care', 'clinical.patient.contacts-read', 'clinical.note.write', 'clinical.signed-document.read-own',
    'clinical.patient-file.write-care', 'inventory.item.read', 'inventory.stock.read',
    'drive.file.use', 'insights.dashboard.read', 'insights.analytics.read-own',
  ],
  NURSE: [
    'schedule.appointment.read', 'schedule.appointment.write', 'schedule.practitioner.read', 'schedule.service.read',
    'clinical.patient.read', 'clinical.patient.contacts-read',
    'inventory.item.read', 'inventory.item.write', 'inventory.document.write', 'inventory.stock.read', 'inventory.marking.read',
    'drive.file.use', 'insights.dashboard.read',
  ],
  CASHIER: [
    'schedule.appointment.read', 'schedule.practitioner.read', 'schedule.service.read', 'clinical.patient.read',
    'schedule.registry-task.read', 'finance.invoice.read', 'finance.invoice.write', 'finance.invoice.set-amount',
    'drive.file.use', 'insights.dashboard.read',
  ],
  SUPPORT: [
    'identity.staff.read', 'identity.staff.reset-password-platform', 'schedule.appointment.read', 'schedule.practitioner.read',
    'schedule.service.read', 'inventory.item.read', 'inventory.stock.read', 'inventory.marking.read', 'inventory.marking.manage',
    'drive.file.use', 'drive.admin', 'insights.dashboard.read', 'insights.analytics.read', 'audit.event.read-platform',
    'communications.delivery.read', 'communications.delivery.retry',
  ],
}

/** Разрешения сотрудника: с сервера, если пришли, иначе по роли. */
export function permissionsOf(staff: Pick<StaffItem, 'role' | 'permissions'>): Permission[] {
  return staff.permissions && staff.permissions.length > 0 ? (staff.permissions as Permission[]) : rolePermissions[staff.role] ?? []
}

/** Есть ли у роли хотя бы одно из разрешений. */
export function can(role: StaffRole, ...permissions: Permission[]) {
  const granted = rolePermissions[role] ?? []
  return permissions.some((permission) => granted.includes(permission))
}

/** Роли, которые сотрудник может назначить (и обслуживать): не выше своих прав. */
export function assignableRoles(role: StaffRole): StaffRole[] {
  const all: StaffRole[] = ['OWNER', 'SENIOR_ADMINISTRATOR', 'ADMINISTRATOR', 'MANAGER', 'DOCTOR', 'NURSE', 'CASHIER', 'SUPPORT']
  return all.filter((target) => {
    if (target === 'OWNER' || target === 'SENIOR_ADMINISTRATOR') return can(role, 'identity.role.assign-privileged')
    if (target === 'ADMINISTRATOR' || target === 'SUPPORT') return can(role, 'identity.role.assign-admin')
    return can(role, 'identity.staff.manage')
  })
}
