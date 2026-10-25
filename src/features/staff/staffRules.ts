import type { CreateStaffInput } from '../../api/staff'
import type { Practitioner, StaffItem, StaffRole } from '../../api/types'
import { MIN_PASSWORD } from '../auth/password'
import { fromRows, type ShiftRow } from '../settings/shifts'

/** Карточка врача, которая заводится вместе с учётной записью. */
export type NewCardDraft = { specialty: string; room: string; rows: ShiftRow[] }

/**
 * Черновик сотрудника. Врачу — либо practitionerId карточки из справочника, либо newPractitioner:
 * сервер создаст карточку в той же транзакции, что и учётную запись.
 */
export type StaffDraft = {
  fullName: string
  username: string
  password: string
  role: StaffRole
  practitionerId: string | null
  newPractitioner?: NewCardDraft | null
}

/** Карточки врачей этой клиники, к которым ещё не привязана учётная запись. */
export function freePractitioners(practitioners: Practitioner[], existing: StaffItem[]) {
  return practitioners.filter((item) => !existing.some((account) => account.practitionerId === item.id))
}

/** Новая карточка без единой смены: создать можно, записать к врачу — нет. */
export function noShiftsYet(draft: StaffDraft) {
  return draft.role === 'DOCTOR' && Boolean(draft.newPractitioner) && !draft.newPractitioner?.rows.some((row) => row.enabled)
}

/** Те же правила, что на сервере: врачу нужна карточка — новая или из справочника, остальным — нет. */
export function staffProblem(draft: StaffDraft, existing: StaffItem[], practitionerIds: string[]) {
  if (!draft.fullName.trim()) return 'Укажите ФИО'
  if (!/^[a-zA-Z0-9._-]{3,80}$/.test(draft.username)) return 'Логин: латиница, цифры, точка, дефис или подчёркивание, от 3 символов'
  if (existing.some((item) => item.username === draft.username.toLowerCase())) return 'Такой логин уже есть'
  if (draft.password.length < MIN_PASSWORD) return `Пароль должен быть не короче ${MIN_PASSWORD} символов`
  if (draft.role === 'DOCTOR') {
    if (draft.newPractitioner) {
      if (draft.practitionerId) return 'Выберите одно: новая карточка или карточка из справочника'
      if (!draft.newPractitioner.specialty.trim()) return 'Укажите специальность'
      if (draft.newPractitioner.room.trim().length > 80) return 'Кабинет — не длиннее 80 символов'
      const shifts = fromRows(draft.newPractitioner.rows)
      if ('error' in shifts) return shifts.error
      return null
    }
    if (!draft.practitionerId) return 'Для врача выберите карточку врача'
    if (!practitionerIds.includes(draft.practitionerId)) return 'Врач не найден'
    if (existing.some((item) => item.practitionerId === draft.practitionerId)) return 'У этого врача уже есть учётная запись'
  } else if (draft.practitionerId || draft.newPractitioner) {
    return 'Карточка врача привязывается только к роли «Врач»'
  }
  return null
}

/** Тело POST /staff. Вызывать после staffProblem: смены уже проверены. */
export function toCreateStaffInput(draft: StaffDraft): CreateStaffInput {
  const base = { fullName: draft.fullName, username: draft.username, password: draft.password, role: draft.role, practitionerId: draft.practitionerId }
  if (draft.role !== 'DOCTOR' || !draft.newPractitioner) return base
  const shifts = fromRows(draft.newPractitioner.rows)
  return {
    ...base,
    practitionerId: null,
    newPractitioner: { specialty: draft.newPractitioner.specialty.trim(), room: draft.newPractitioner.room.trim(), shifts: 'error' in shifts ? [] : shifts.shifts },
  }
}

/** Отключить себя нельзя — иначе клиника может остаться без администратора. */
export const canDeactivate = (target: StaffItem, selfId: string) => target.active && target.id !== selfId
