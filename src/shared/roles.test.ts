import { describe, expect, it } from 'vitest'
import { canManageClinic, canSeeAnalytics, canSeeCashier, canSeeRegistry, canSeeSignedDocuments, canSeeWaitlist, profileOf, roleLabels } from './roles'

describe('interface roles', () => {
  it('keeps the role as the API code and shows its on-screen label', () => {
    expect(roleLabels.CASHIER).toBe('Кассир')
    expect(roleLabels.DOCTOR).toBe('Врач')
    expect(profileOf({ fullName: 'Ольга Лебедева', role: 'DOCTOR' })).toEqual({ name: 'Ольга Лебедева', initials: 'ОЛ', role: 'Врач' })
  })

  it('opens cashier to cashier and administrator, analytics to administrator and manager', () => {
    expect(canSeeCashier('CASHIER')).toBe(true)
    expect(canSeeCashier('ADMINISTRATOR')).toBe(true)
    expect(canSeeCashier('DOCTOR')).toBe(false)
    expect(canSeeCashier('MANAGER')).toBe(false)
    expect(canSeeAnalytics('CASHIER')).toBe(false)
    expect(canSeeAnalytics('MANAGER')).toBe(true)
  })

  it('registry is closed to doctors, settings to everyone but the administrator', () => {
    expect(canSeeRegistry('DOCTOR')).toBe(false)
    expect(canSeeRegistry('CASHIER')).toBe(true)
    expect(canSeeWaitlist('CASHIER')).toBe(false)
    expect(canSeeWaitlist('MANAGER')).toBe(true)
    expect(canManageClinic('ADMINISTRATOR')).toBe(true)
    expect(canManageClinic('MANAGER')).toBe(false)
  })

  it('signed documents are visible to doctor and manager, not to administrator and cashier', () => {
    expect(canSeeSignedDocuments('DOCTOR')).toBe(true)
    expect(canSeeSignedDocuments('MANAGER')).toBe(true)
    expect(canSeeSignedDocuments('ADMINISTRATOR')).toBe(false)
    expect(canSeeSignedDocuments('CASHIER')).toBe(false)
  })
})
