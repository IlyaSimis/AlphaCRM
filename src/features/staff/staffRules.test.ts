import { describe, expect, it } from 'vitest'
import { LEBEDEVA_ID, PETROV_ID } from '../../api/client'
import { sandboxStaff } from '../../app/demoData'
import { demoPractitioners } from '../../api/schedule'
import { toRows } from '../settings/shifts'
import { canDeactivate, freePractitioners, noShiftsYet, staffProblem, toCreateStaffInput, type StaffDraft } from './staffRules'

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

  it('a new card needs a specialty and valid shifts; an empty schedule is allowed but flagged', () => {
    const rows = toRows([])
    const card = { specialty: 'Психиатр', room: ' 204 ', rows }
    const doctor = draft({ role: 'DOCTOR', newPractitioner: card })
    expect(staffProblem(doctor, sandboxStaff, doctors)).toBeNull()
    expect(noShiftsYet(doctor)).toBe(true)
    expect(staffProblem(draft({ role: 'DOCTOR', newPractitioner: { ...card, specialty: ' ' } }), sandboxStaff, doctors)).toBe('Укажите специальность')
    const broken = rows.map((row) => row.day === 2 ? { ...row, enabled: true, startsAt: '15:00', endsAt: '09:00' } : row)
    expect(staffProblem(draft({ role: 'DOCTOR', newPractitioner: { ...card, rows: broken } }), sandboxStaff, doctors)).toBe('Вторник: конец смены должен быть позже начала')
    expect(staffProblem(draft({ role: 'DOCTOR', practitionerId: doctors[2], newPractitioner: card }), sandboxStaff, doctors)).toMatch(/^Выберите одно/)
    expect(staffProblem(draft({ newPractitioner: card }), sandboxStaff, doctors)).toBe('Карточка врача привязывается только к роли «Врач»')

    const working = rows.map((row) => row.day === 3 ? { ...row, enabled: true } : row)
    const withShift = draft({ role: 'DOCTOR', newPractitioner: { ...card, rows: working } })
    expect(noShiftsYet(withShift)).toBe(false)
    expect(toCreateStaffInput(withShift)).toEqual({
      fullName: 'Ирина Белова', username: 'belova', password: 'long-password', role: 'DOCTOR', practitionerId: null,
      newPractitioner: { specialty: 'Психиатр', room: '204', shifts: [{ dayOfWeek: 3, startsAt: '09:00:00', endsAt: '18:00:00' }] },
    })
  })

  it('only cards without an account can be bound', () => {
    expect(freePractitioners(demoPractitioners, sandboxStaff).map((item) => item.fullName)).toEqual(['Ольга Лебедева'])
  })
})
