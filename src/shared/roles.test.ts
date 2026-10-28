import { describe, expect, it } from 'vitest'
import { assignableRoles, can, rolePermissions, rolesOf } from './permissions'
import { rolesChangeProblem } from '../features/staff/staffRules'
import { canManagePrices, canSeeAnalytics, canSeeAudit, canSeeCashier, canSeeDrive, canSeeInventory, canSeeRegistry, canSeeSignedDocuments, canSeeWaitlist, canTakePayments, canWriteClinical, profileOf, roleLabels, roleText, staffRolesOf } from './roles'

describe('interface roles', () => {
  it('keeps the role as the API code and shows its on-screen label', () => {
    expect(roleLabels.CASHIER).toBe('Кассир')
    expect(roleLabels.DOCTOR).toBe('Врач')
    expect(roleLabels.NURSE).toBe('Медперсонал')
    expect(roleLabels.OWNER).toBe('Владелец клиники')
    expect(profileOf({ fullName: 'Ольга Лебедева', role: 'DOCTOR' })).toEqual({ name: 'Ольга Лебедева', initials: 'ОЛ', role: 'Врач' })
  })

  it('cashier: payments; manager: charges read-only; analytics for administrators, manager and support', () => {
    expect(canSeeCashier('CASHIER')).toBe(true)
    expect(canTakePayments('CASHIER')).toBe(true)
    expect(canTakePayments('ADMINISTRATOR')).toBe(true)
    expect(canSeeCashier('MANAGER')).toBe(true)
    expect(canTakePayments('MANAGER')).toBe(false)
    expect(canSeeCashier('DOCTOR')).toBe(false)
    expect(canSeeAnalytics('CASHIER')).toBe(false)
    expect(canSeeAnalytics('MANAGER')).toBe(true)
    expect(canSeeAudit('SUPPORT')).toBe(true)
    expect(canSeeAudit('NURSE')).toBe(false)
  })

  it('registry is closed to doctors; prices are for administrators and the owner only', () => {
    expect(canSeeRegistry('DOCTOR')).toBe(false)
    expect(canSeeRegistry('CASHIER')).toBe(true)
    expect(canSeeWaitlist('CASHIER')).toBe(false)
    expect(canSeeWaitlist('MANAGER')).toBe(true)
    expect(canManagePrices('ADMINISTRATOR')).toBe(true)
    expect(canManagePrices('OWNER')).toBe(true)
    expect(canManagePrices('MANAGER')).toBe(false)
  })

  it('clinical text belongs to the doctor only, even for the owner', () => {
    for (const role of Object.keys(rolePermissions) as Array<keyof typeof rolePermissions>) {
      expect(canWriteClinical(role)).toBe(role === 'DOCTOR')
    }
    expect(canSeeSignedDocuments('DOCTOR')).toBe(true)
    expect(canSeeSignedDocuments('MANAGER')).toBe(true)
    expect(canSeeSignedDocuments('OWNER')).toBe(false)
    expect(canSeeSignedDocuments('CASHIER')).toBe(false)
  })

  it('inventory for nurse, manager and administrators; drive for everyone', () => {
    expect(canSeeInventory('NURSE')).toBe(true)
    expect(canSeeInventory('CASHIER')).toBe(false)
    expect(can('NURSE', 'inventory.document.write')).toBe(true)
    expect(can('DOCTOR', 'inventory.document.write')).toBe(false)
    expect(canSeeDrive('CASHIER')).toBe(true)
    expect(can('SUPPORT', 'drive.clinic.admin')).toBe(true)
  })

  it('nobody assigns a role above their own', () => {
    expect(assignableRoles('OWNER')).toContain('SENIOR_ADMINISTRATOR')
    expect(assignableRoles('ADMINISTRATOR')).not.toContain('OWNER')
    expect(assignableRoles('ADMINISTRATOR')).toContain('SUPPORT')
    expect(assignableRoles('MANAGER')).toEqual(['MANAGER', 'DOCTOR', 'NURSE', 'CASHIER'])
    expect(assignableRoles('CASHIER')).toEqual([])
  })

  it('several roles add up their permissions and read as a list', () => {
    const roles = ['NURSE', 'CASHIER'] as const
    expect(canSeeInventory(roles)).toBe(true)
    expect(canTakePayments(roles)).toBe(true)
    expect(canWriteClinical(roles)).toBe(false)
    expect(roleText(roles)).toBe('Медперсонал, Кассир')
    expect(rolesOf({ role: 'CASHIER', roles: ['CASHIER'] })).toBe('CASHIER')
    expect(staffRolesOf({ role: 'NURSE', roles: ['CASHIER', 'NURSE'] })).toEqual(['NURSE', 'CASHIER'])
    expect(profileOf({ fullName: 'Ирина Котова', role: 'NURSE', roles: ['NURSE', 'CASHIER'] }).role).toBe('Медперсонал, Кассир')
    expect(assignableRoles(['MANAGER', 'CASHIER'])).not.toContain('ADMINISTRATOR')
    expect(rolesChangeProblem([])).not.toBeNull()
  })
})
