import { describe, expect, it } from 'vitest'
import { canOpen, firstPageOf, navigationFor, sectionsFor, serviceOf, servicesFor, startPage } from './pages'

describe('services and pages by role', () => {
  it('the top bar has five services; every page belongs to one of them', () => {
    expect(servicesFor('ADMINISTRATOR')).toEqual(['dashboards', 'patients', 'inventory', 'finance', 'drive'])
    expect(serviceOf('cashier')).toBe('finance')
    expect(serviceOf('prices')).toBe('finance')
    expect(serviceOf('sales')).toBe('finance')
    expect(canOpen('sales', 'CASHIER')).toBe(false)
    expect(serviceOf('patient')).toBe('patients')
    expect(serviceOf('visit')).toBe('patients')
    expect(serviceOf('hours')).toBe('patients')
    expect(serviceOf('stock-items')).toBe('inventory')
    expect(serviceOf('drive-trash')).toBe('drive')
    expect(serviceOf('staff')).toBe('dashboards')
  })

  it('the administrator sees everything operational, grouped by service', () => {
    expect(navigationFor('ADMINISTRATOR')).toEqual([
      'today', 'analytics', 'staff', 'audit', 'outbox',
      'schedule', 'patients', 'registry', 'hours',
      'stock-balances', 'stock-items', 'stock-documents', 'stock-marking', 'stock-norms',
      'cashier', 'sales', 'prices',
      'drive', 'drive-shared', 'drive-trash',
    ])
    expect(navigationFor('OWNER')).toEqual(navigationFor('ADMINISTRATOR'))
    expect(sectionsFor('dashboards', 'ADMINISTRATOR').map((section) => section.label)).toEqual(['ОБЗОР', 'УПРАВЛЕНИЕ'])
  })

  it('a cashier has no inventory service; a doctor has no finance and no dashboards tab (his dashboard is above the schedule)', () => {
    expect(servicesFor('CASHIER')).toEqual(['dashboards', 'patients', 'finance', 'drive'])
    expect(navigationFor('CASHIER')).toEqual(['today', 'schedule', 'patients', 'registry', 'cashier', 'drive', 'drive-shared', 'drive-trash'])
    expect(servicesFor('DOCTOR')).toEqual(['patients', 'inventory', 'drive'])
    expect(sectionsFor('inventory', 'DOCTOR').flatMap((section) => section.pages)).toEqual(['stock-balances', 'stock-items'])
    expect(canOpen('cashier', 'DOCTOR')).toBe(false)
    expect(canOpen('visit', 'DOCTOR')).toBe(true)
    expect(canOpen('visit', 'ADMINISTRATOR')).toBe(false)
    expect(canOpen('documents', 'ADMINISTRATOR')).toBe(false)
  })

  it('manager sees prices read-only in finance, nurse works with stock, support has no patients', () => {
    expect(sectionsFor('finance', 'MANAGER').flatMap((section) => section.pages)).toEqual(['cashier', 'sales', 'prices'])
    expect(canOpen('outbox', 'MANAGER')).toBe(false)
    expect(sectionsFor('inventory', 'NURSE').flatMap((section) => section.pages)).toEqual(['stock-balances', 'stock-items', 'stock-documents', 'stock-marking', 'stock-norms'])
    expect(servicesFor('SUPPORT')).toEqual(['dashboards', 'patients', 'inventory', 'drive'])
    expect(canOpen('patient', 'SUPPORT')).toBe(false)
  })

  it('a service tab opens its first page available to the role', () => {
    expect(firstPageOf('dashboards', 'ADMINISTRATOR')).toBe('today')
    expect(firstPageOf('dashboards', 'DOCTOR')).toBeNull()
    expect(firstPageOf('inventory', 'NURSE')).toBe('stock-balances')
    expect(firstPageOf('finance', 'NURSE')).toBeNull()
  })

  it('«Сегодня» is the start page with the personal dashboard; the doctor starts with the schedule', () => {
    expect(startPage('ADMINISTRATOR')).toBe('today')
    expect(startPage('NURSE')).toBe('today')
    expect(startPage('DOCTOR')).toBe('schedule')
    expect(canOpen('today', 'DOCTOR')).toBe(false)
    expect(canOpen('today', 'CASHIER')).toBe(true)
  })
})
