import { describe, expect, it } from 'vitest'
import { canOpen, navigationFor } from './pages'

describe('pages by role', () => {
  it('a cashier sees registry and cashier but not analytics', () => {
    expect(navigationFor('CASHIER')).toEqual(['schedule', 'patients', 'registry', 'cashier'])
  })

  it('the administrator sees everything, including hours, prices and staff', () => {
    expect(navigationFor('ADMINISTRATOR')).toEqual(['schedule', 'patients', 'registry', 'cashier', 'analytics', 'audit', 'settings', 'staff', 'outbox'])
  })

  it('doctor and manager see neither cashier nor settings', () => {
    expect(navigationFor('DOCTOR')).toEqual(['schedule', 'patients', 'documents'])
    expect(navigationFor('MANAGER')).toEqual(['schedule', 'patients', 'documents', 'registry', 'analytics', 'audit'])
    expect(canOpen('cashier', 'DOCTOR')).toBe(false)
    expect(canOpen('staff', 'MANAGER')).toBe(false)
    expect(canOpen('documents', 'ADMINISTRATOR')).toBe(false)
    expect(canOpen('documents', 'CASHIER')).toBe(false)
    expect(canOpen('visit', 'DOCTOR')).toBe(true)
    expect(canOpen('visit', 'ADMINISTRATOR')).toBe(false)
  })
})
