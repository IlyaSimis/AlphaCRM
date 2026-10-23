import type { StaffItem, StaffRole } from '../../api/types'
import { MIN_PASSWORD } from '../auth/password'

export type StaffDraft = { fullName: string; username: string; password: string; role: StaffRole; practitionerId: string | null }

/** Те же правила, что на сервере: врачу нужен врач из справочника, остальным — нет. */
export function staffProblem(draft: StaffDraft, existing: StaffItem[], practitionerIds: string[]) {
  if (!draft.fullName.trim()) return 'Укажите ФИО'
  if (!/^[a-zA-Z0-9._-]{3,80}$/.test(draft.username)) return 'Логин: латиница, цифры, точка, дефис или подчёркивание, от 3 символов'
  if (existing.some((item) => item.username === draft.username.toLowerCase())) return 'Такой логин уже есть'
  if (draft.password.length < MIN_PASSWORD) return `Пароль должен быть не короче ${MIN_PASSWORD} символов`
  if (draft.role === 'DOCTOR') {
    if (!draft.practitionerId) return 'Для врача выберите карточку врача'
    if (!practitionerIds.includes(draft.practitionerId)) return 'Врач не найден'
    if (existing.some((item) => item.practitionerId === draft.practitionerId)) return 'У этого врача уже есть учётная запись'
  } else if (draft.practitionerId) {
    return 'Карточка врача привязывается только к роли «Врач»'
  }
  return null
}

/** Отключить себя нельзя — иначе клиника может остаться без администратора. */
export const canDeactivate = (target: StaffItem, selfId: string) => target.active && target.id !== selfId
