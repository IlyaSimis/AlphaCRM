import { describe, expect, it } from 'vitest'
import { canOpen, navigationFor, startPage } from './pages'

describe('pages by role', () => {
  it('a cashier sees registry, cashier and drive but not analytics', () => {
    expect(navigationFor('CASHIER')).toEqual(['today', 'schedule', 'patients', 'registry', 'cashier', 'drive'])
  })

  it('the administrator sees everything operational, including inventory, drive, hours, prices and staff', () => {
    expect(navigationFor('ADMINISTRATOR')).toEqual(['today', 'schedule', 'patients', 'registry', 'cashier', 'inventory', 'drive', 'analytics', 'audit', 'settings', 'staff', 'outbox'])
    expect(navigationFor('OWNER')).toEqual(navigationFor('ADMINISTRATOR'))
  })

  it('doctor and manager see the pages of their matrix rows', () => {
    expect(navigationFor('DOCTOR')).toEqual(['schedule', 'patients', 'documents', 'inventory', 'drive'])
    expect(navigationFor('MANAGER')).toEqual(['today', 'schedule', 'patients', 'documents', 'registry', 'cashier', 'inventory', 'drive', 'analytics', 'audit', 'settings', 'staff'])
    expect(canOpen('cashier', 'DOCTOR')).toBe(false)
    expect(canOpen('outbox', 'MANAGER')).toBe(false)
    expect(canOpen('documents', 'ADMINISTRATOR')).toBe(false)
    expect(canOpen('documents', 'CASHIER')).toBe(false)
    expect(canOpen('visit', 'DOCTOR')).toBe(true)
    expect(canOpen('visit', 'ADMINISTRATOR')).toBe(false)
  })

  it('nurse and support: no patients for support, inventory for both', () => {
    expect(navigationFor('NURSE')).toEqual(['today', 'schedule', 'patients', 'inventory', 'drive'])
    expect(navigationFor('SUPPORT')).toEqual(['today', 'schedule', 'inventory', 'drive', 'analytics', 'audit', 'staff', 'outbox'])
    expect(canOpen('patient', 'SUPPORT')).toBe(false)
  })

  it('«Сегодня» is the start page with the personal dashboard; the doctor starts with the schedule and sees the dashboard there', () => {
    expect(startPage('ADMINISTRATOR')).toBe('today')
    expect(startPage('CASHIER')).toBe('today')
    expect(startPage('MANAGER')).toBe('today')
    expect(startPage('NURSE')).toBe('today')
    expect(startPage('DOCTOR')).toBe('schedule')
    expect(canOpen('today', 'DOCTOR')).toBe(false)
    expect(canOpen('today', 'CASHIER')).toBe(true)
  })
})
