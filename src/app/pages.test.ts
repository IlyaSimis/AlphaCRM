import { describe, expect, it } from 'vitest'
import { canOpen, firstPageOf, navigationFor, sectionsFor, serviceOf, servicesFor, startPage } from './pages'

describe('services and pages by role', () => {
  it('the top bar has seven services; every page belongs to one of them', () => {
    expect(servicesFor('ADMINISTRATOR')).toEqual(['dashboards', 'management', 'patients', 'documents', 'inventory', 'finance', 'drive'])
    expect(serviceOf('cashier')).toBe('finance')
    expect(serviceOf('prices')).toBe('finance')
    expect(serviceOf('sales')).toBe('finance')
    expect(canOpen('sales', 'CASHIER')).toBe(false)
    expect(serviceOf('patient')).toBe('patients')
    expect(serviceOf('visit')).toBe('patients')
    expect(serviceOf('hours')).toBe('management')
    expect(serviceOf('contract-templates')).toBe('documents')
    expect(serviceOf('stock-items')).toBe('inventory')
    expect(serviceOf('drive-trash')).toBe('drive')
    expect(serviceOf('staff')).toBe('management')
    expect(serviceOf('messages')).toBe('dashboards')
    expect(canOpen('messages', 'DOCTOR')).toBe(true)
  })

  it('the administrator sees everything operational, grouped by service', () => {
    expect(navigationFor('ADMINISTRATOR')).toEqual([
      'today', 'workspace', 'messages', 'analytics', 'hr-me', 'staff', 'hours', 'audit', 'integrations',
      'schedule', 'patients', 'registry', 'contract-templates',
      'stock-balances', 'stock-items', 'stock-documents', 'stock-marking', 'stock-norms',
      'cashier', 'sales', 'prices',
      'drive', 'drive-shared', 'drive-trash',
    ])
    // Владелец и старший администратор вдобавок видят журнал экстренных доступов (K1), владелец — ставки.
    const withLog = navigationFor('ADMINISTRATOR').flatMap((page) => (page === 'audit' ? ['audit', 'break-glass'] : [page]))
    expect(navigationFor('SENIOR_ADMINISTRATOR')).toEqual(withLog)
    expect(navigationFor('OWNER')).toEqual(withLog.flatMap((page) => (page === 'hours' ? ['hours', 'hr'] : [page])))
    expect(canOpen('hr', 'SENIOR_ADMINISTRATOR')).toBe(false)
    expect(canOpen('break-glass', 'MANAGER')).toBe(false)
    expect(sectionsFor('dashboards', 'ADMINISTRATOR').map((section) => section.label)).toEqual(['ОБЗОР'])
    expect(sectionsFor('management', 'ADMINISTRATOR').map((section) => section.label)).toEqual(['УПРАВЛЕНИЕ'])
  })

  it('a cashier and a doctor have documents but no inventory; every role has a personal workspace', () => {
    expect(servicesFor('CASHIER')).toEqual(['dashboards', 'patients', 'documents', 'finance', 'drive'])
    expect(navigationFor('CASHIER')).toEqual(['today', 'workspace', 'messages', 'hr-me', 'schedule', 'patients', 'registry', 'contract-templates', 'cashier', 'drive', 'drive-shared', 'drive-trash'])
    expect(servicesFor('DOCTOR')).toEqual(['dashboards', 'patients', 'documents', 'drive'])
    expect(sectionsFor('dashboards', 'DOCTOR').flatMap((section) => section.pages)).toEqual(['workspace', 'messages', 'hr-me'])
    expect(sectionsFor('documents', 'DOCTOR').flatMap((section) => section.pages)).toEqual(['contract-templates'])
    expect(sectionsFor('inventory', 'DOCTOR')).toEqual([])
    expect(canOpen('cashier', 'DOCTOR')).toBe(false)
    expect(canOpen('visit', 'DOCTOR')).toBe(true)
    expect(canOpen('visit', 'ADMINISTRATOR')).toBe(false)
    expect(canOpen('documents', 'ADMINISTRATOR')).toBe(false)
  })

  it('medical staff works with stock and contracts; support has documents but no patients or stock', () => {
    expect(sectionsFor('finance', 'MANAGER').flatMap((section) => section.pages)).toEqual(['cashier', 'sales', 'prices'])
    expect(sectionsFor('documents', 'MANAGER').flatMap((section) => section.pages)).toEqual(['contract-templates'])
    expect(canOpen('outbox', 'MANAGER')).toBe(false)
    expect(sectionsFor('management', 'MANAGER').flatMap((section) => section.pages)).toEqual(['staff', 'hours', 'audit'])
    for (const role of ['NURSE', 'SENIOR_NURSE'] as const) {
      expect(sectionsFor('inventory', role).flatMap((section) => section.pages)).toEqual(['stock-balances', 'stock-items', 'stock-documents', 'stock-marking', 'stock-norms'])
      expect(canOpen('contract-templates', role)).toBe(true)
    }
    expect(servicesFor('SUPPORT')).toEqual(['dashboards', 'management', 'patients', 'documents', 'drive'])
    expect(sectionsFor('documents', 'SUPPORT').flatMap((section) => section.pages)).toEqual(['contract-templates'])
    expect(canOpen('patient', 'SUPPORT')).toBe(false)
  })

  it('a service tab opens its first page available to the role', () => {
    expect(firstPageOf('dashboards', 'ADMINISTRATOR')).toBe('today')
    expect(firstPageOf('dashboards', 'DOCTOR')).toBe('workspace')
    expect(firstPageOf('management', 'MANAGER')).toBe('staff')
    expect(firstPageOf('documents', 'ADMINISTRATOR')).toBe('contract-templates')
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
