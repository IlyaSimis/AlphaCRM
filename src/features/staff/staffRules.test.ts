import { describe, expect, it } from 'vitest'
import { LEBEDEVA_ID, PETROV_ID } from '../../api/client'
import { sandboxStaff } from '../../app/demoData'
import { canDeactivate, staffProblem, type StaffDraft } from './staffRules'

const draft = (patch: Partial<StaffDraft> = {}): StaffDraft => ({ fullName: 'Ирина Белова', username: 'belova', password: 'long-password', role: 'CASHIER', practitionerId: null, ...patch })
const doctors = [PETROV_ID, LEBEDEVA_ID, '00000000-0000-0000-0000-000000000203']

describe('staff accounts', () => {
  it('accepts a new cashier', () => {
    expect(staffProblem(draft(), sandboxStaff, doctors)).toBeNull()
  })

  it('a doctor needs a free practitioner card, other roles none', () => {
    expect(staffProblem(draft({ role: 'DOCTOR' }), sandboxStaff, doctors)).toBe('Для врача выберите карточку врача')
    expect(staffProblem(draft({ role: 'DOCTOR', practitionerId: 'unknown' }), sandboxStaff, doctors)).toBe('Врач не найден')
    expect(staffProblem(draft({ role: 'DOCTOR', practitionerId: PETROV_ID }), sandboxStaff, doctors)).toBe('У этого врача уже есть учётная запись')
    expect(staffProblem(draft({ role: 'DOCTOR', practitionerId: doctors[2] }), sandboxStaff, doctors)).toBeNull()
    expect(staffProblem(draft({ practitionerId: PETROV_ID }), sandboxStaff, doctors)).toBe('Карточка врача привязывается только к роли «Врач»')
  })

  it('checks username and password', () => {
    expect(staffProblem(draft({ username: 'Sandbox-Admin' }), sandboxStaff, doctors)).toBe('Такой логин уже есть')
    expect(staffProblem(draft({ username: 'и' }), sandboxStaff, doctors)).toMatch(/^Логин/)
    expect(staffProblem(draft({ password: 'short' }), sandboxStaff, doctors)).toBe('Пароль должен быть не короче 8 символов')
  })

  it('the administrator cannot deactivate themselves', () => {
    const admin = sandboxStaff[0]
    expect(canDeactivate(admin, admin.id)).toBe(false)
    expect(canDeactivate(sandboxStaff[3], admin.id)).toBe(true)
  })
})
