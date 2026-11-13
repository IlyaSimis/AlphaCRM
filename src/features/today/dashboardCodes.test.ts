import { describe, expect, it } from 'vitest'
import { dashboardCodes } from './dashboardCodes'

describe('role dashboards', () => {
  it('shows manager and doctor the metrics needed for their work', () => {
    expect(dashboardCodes('MANAGER')).toEqual(['revenue.month', 'schedule.load', 'appointments.month.noShow', 'charges.unpaid', 'salary.month'])
    expect(dashboardCodes('DOCTOR')).toContain('documents.unsigned')
    expect(dashboardCodes('DOCTOR')).not.toContain('revenue.month')
  })

  it('keeps support away from patient and clinical metrics', () => {
    expect(dashboardCodes('SUPPORT')).toEqual(['delivery.failed', 'events.backlog'])
    expect(dashboardCodes('NURSE')).toContain('stock.shortage')
    expect(dashboardCodes('CASHIER')).toContain('charges.unpaid')
  })
})
