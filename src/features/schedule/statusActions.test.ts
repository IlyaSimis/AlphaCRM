import { describe, expect, it } from 'vitest'
import { LEBEDEVA_ID, PETROV_ID } from '../../api/client'
import type { AppointmentStatus } from '../../api/types'
import type { Role } from '../../shared/roles'
import { availableActions, canStartVisit } from './statusActions'

const targets = (statusCode: AppointmentStatus, role: Role, practitionerId = PETROV_ID, doctorId = PETROV_ID) =>
  availableActions({ statusCode, practitionerId }, role, doctorId).map((action) => action.to)

describe('переходы статуса записи', () => {
  it('администратор подтверждает, отмечает приход и неявку', () => {
    expect(targets('BOOKED', 'Администратор')).toEqual(['CONFIRMED', 'ARRIVED', 'NO_SHOW'])
    expect(targets('CONFIRMED', 'Администратор')).toEqual(['ARRIVED', 'NO_SHOW'])
    expect(targets('ARRIVED', 'Администратор')).toEqual([])
  })

  it('врач ведёт приём только по записям выбранного врача', () => {
    expect(targets('ARRIVED', 'Врач')).toEqual(['IN_PROGRESS', 'COMPLETED'])
    expect(targets('IN_PROGRESS', 'Врач')).toEqual(['COMPLETED'])
    expect(targets('ARRIVED', 'Врач', LEBEDEVA_ID)).toEqual([])
    expect(targets('ARRIVED', 'Врач', LEBEDEVA_ID, LEBEDEVA_ID)).toEqual(['IN_PROGRESS', 'COMPLETED'])
    expect(targets('BOOKED', 'Врач')).toEqual([])
  })

  it('из итоговых статусов переходов нет, менеджер и кассир статусы не меняют', () => {
    for (const status of ['COMPLETED', 'NO_SHOW', 'CANCELLED'] as const) {
      expect(targets(status, 'Администратор')).toEqual([])
      expect(targets(status, 'Врач')).toEqual([])
    }
    expect(targets('BOOKED', 'Менеджер')).toEqual([])
    expect(targets('BOOKED', 'Кассир')).toEqual([])
  })

  it('начать приём можно по своей записи, когда пациент пришёл', () => {
    expect(canStartVisit({ statusCode: 'ARRIVED', practitionerId: LEBEDEVA_ID }, 'Врач', LEBEDEVA_ID)).toBe(true)
    expect(canStartVisit({ statusCode: 'IN_PROGRESS', practitionerId: PETROV_ID }, 'Врач', PETROV_ID)).toBe(true)
    expect(canStartVisit({ statusCode: 'CONFIRMED', practitionerId: PETROV_ID }, 'Врач', PETROV_ID)).toBe(false)
    expect(canStartVisit({ statusCode: 'ARRIVED', practitionerId: PETROV_ID }, 'Врач', LEBEDEVA_ID)).toBe(false)
    expect(canStartVisit({ statusCode: 'ARRIVED', practitionerId: PETROV_ID }, 'Администратор', PETROV_ID)).toBe(false)
  })
})
