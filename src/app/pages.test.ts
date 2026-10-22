import { describe, expect, it } from 'vitest'
import { canOpen, navigationFor } from './pages'

describe('страницы по ролям', () => {
  it('кассир видит кассу, но не аналитику', () => {
    expect(navigationFor('Кассир')).toEqual(['schedule', 'patients', 'cashier'])
  })

  it('администратор видит кассу, аналитику и журнал', () => {
    expect(navigationFor('Администратор')).toEqual(['schedule', 'patients', 'cashier', 'analytics', 'audit'])
  })

  it('врач и менеджер не видят кассу', () => {
    expect(navigationFor('Врач')).toEqual(['schedule', 'patients'])
    expect(navigationFor('Менеджер')).toEqual(['schedule', 'patients', 'analytics', 'audit'])
    expect(canOpen('cashier', 'Врач')).toBe(false)
    expect(canOpen('visit', 'Врач')).toBe(true)
    expect(canOpen('visit', 'Администратор')).toBe(false)
  })
})
