import { describe, expect, it } from 'vitest'
import { LEBEDEVA_ID, PETROV_ID } from '../../api/client'
import type { AppointmentStatus } from '../../api/types'
import type { Role } from '../../shared/roles'
import { availableActions, canStartVisit } from './statusActions'

const targets = (statusCode: AppointmentStatus, role: Role, practitionerId = PETROV_ID, doctorId = PETROV_ID) =>
  availableActions({ statusCode, practitionerId }, role, doctorId).map((action) => action.to)

describe('appointment status transitions', () => {
  it('the administrator confirms, marks arrival and no-show', () => {
    expect(targets('BOOKED', 'ADMINISTRATOR')).toEqual(['CONFIRMED', 'ARRIVED', 'NO_SHOW'])
    expect(targets('CONFIRMED', 'ADMINISTRATOR')).toEqual(['ARRIVED', 'NO_SHOW'])
    expect(targets('ARRIVED', 'ADMINISTRATOR')).toEqual([])
  })

  it('a doctor runs visits only for the selected doctor appointments', () => {
    expect(targets('ARRIVED', 'DOCTOR')).toEqual(['IN_PROGRESS', 'COMPLETED'])
    expect(targets('IN_PROGRESS', 'DOCTOR')).toEqual(['COMPLETED'])
    expect(targets('ARRIVED', 'DOCTOR', LEBEDEVA_ID)).toEqual([])
    expect(targets('ARRIVED', 'DOCTOR', LEBEDEVA_ID, LEBEDEVA_ID)).toEqual(['IN_PROGRESS', 'COMPLETED'])
    expect(targets('BOOKED', 'DOCTOR')).toEqual([])
  })

  it('no transitions from final statuses; manager and cashier do not change statuses', () => {
    for (const status of ['COMPLETED', 'NO_SHOW', 'CANCELLED'] as const) {
      expect(targets(status, 'ADMINISTRATOR')).toEqual([])
      expect(targets(status, 'DOCTOR')).toEqual([])
    }
    expect(targets('BOOKED', 'MANAGER')).toEqual([])
    expect(targets('BOOKED', 'CASHIER')).toEqual([])
  })

  it('a visit can start from any own appointment except cancelled, no-show and completed', () => {
    for (const status of ['BOOKED', 'CONFIRMED', 'ARRIVED', 'IN_PROGRESS'] as const) {
      expect(canStartVisit({ statusCode: status, practitionerId: PETROV_ID }, 'DOCTOR', PETROV_ID)).toBe(true)
    }
    for (const status of ['CANCELLED', 'NO_SHOW', 'COMPLETED'] as const) {
      expect(canStartVisit({ statusCode: status, practitionerId: PETROV_ID }, 'DOCTOR', PETROV_ID)).toBe(false)
    }
    expect(canStartVisit({ statusCode: 'ARRIVED', practitionerId: PETROV_ID }, 'DOCTOR', LEBEDEVA_ID)).toBe(false)
    expect(canStartVisit({ statusCode: 'ARRIVED', practitionerId: PETROV_ID }, 'ADMINISTRATOR', PETROV_ID)).toBe(false)
  })
})
