import { describe, expect, it } from 'vitest'
import { navigationCatalog, searchNavigation } from './navigationCatalog'

describe('assistant navigation catalog', () => {
  it('opens overdue workspace items from a natural Russian request', () => {
    const [match] = searchNavigation('просроченные задачи', 'ADMINISTRATOR')
    expect(match.id).toBe('workspace-overdue')
    expect(match.score).toBeGreaterThanOrEqual(85)
    expect(match.workspaceIntent).toEqual({ due: 'overdue' })
  })

  it('matches an exact page title without needing the external model', () => {
    expect(searchNavigation('Услуги и цены', 'ADMINISTRATOR')[0].score).toBeGreaterThanOrEqual(120)
    expect(searchNavigation('Услуги и цены', 'ADMINISTRATOR')[0].id).toBe('prices')
  })

  it('only exposes destinations available to the staff role', () => {
    expect(navigationCatalog('DOCTOR').some((item) => item.id === 'cashier')).toBe(false)
    expect(searchNavigation('касса', 'DOCTOR')).toEqual([])
    expect(navigationCatalog('CASHIER').some((item) => item.id === 'cashier')).toBe(true)
  })
})
