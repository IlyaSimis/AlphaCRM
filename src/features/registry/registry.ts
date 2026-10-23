import type { PatientItem, TaskItem, WaitlistItem } from '../../api/types'

/** Название задачи: свободный текст администратора, до 200 символов, как на сервере. */
export const TASK_TITLE_MAX = 200

export function taskTitleProblem(title: string) {
  const trimmed = title.trim()
  if (!trimmed) return 'Напишите, что сделать'
  if (trimmed.length > TASK_TITLE_MAX) return `Не длиннее ${TASK_TITLE_MAX} символов`
  return null
}

/** Справочник «откуда пришёл пациент»: коды совпадают с ReferralSources на сервере. */
export const referralSources: Array<{ value: string; label: string }> = [
  { value: 'RECOMMENDATION', label: 'Рекомендация знакомых' },
  { value: 'WEBSITE', label: 'Сайт клиники' },
  { value: 'INSURANCE', label: 'Страховая компания' },
  { value: 'DOCTOR_REFERRAL', label: 'Направление врача' },
  { value: 'OTHER', label: 'Другое' },
]

export function referralLabel(value?: string | null) {
  if (!value) return 'не указано'
  return referralSources.find((item) => item.value === value)?.label ?? value
}

/** Открытые задачи со сроком сегодня или раньше — как GET /registry/tasks?dueTo=. */
export function dueTasks(tasks: TaskItem[], today: string) {
  return tasks.filter((task) => task.status === 'OPEN' && task.dueDate <= today).sort((left, right) => left.dueDate.localeCompare(right.dueDate))
}

/** Ожидающие этого врача, чей желаемый период включает день освободившегося слота. */
export function matchingWaitlist(entries: WaitlistItem[], practitionerId: string, date: string) {
  return entries.filter((entry) => entry.status === 'ACTIVE' && entry.practitionerId === practitionerId && entry.wishFrom <= date && date <= entry.wishTo)
}

/** Цифры телефона, как в PhoneIndex на сервере: «8 916…» и «+7 916…» — один номер. */
export function phoneKey(phone: string) {
  const digits = phone.replace(/\D/g, '')
  return digits.length === 11 && digits.startsWith('8') ? `7${digits.slice(1)}` : digits
}

/** Другие карточки с тем же телефоном. Для демо-режима и формы нового пациента. */
export function phoneSharedWith(phone: string, patients: PatientItem[], selfId?: string) {
  const key = phoneKey(phone)
  if (!key) return []
  return patients.filter((other) => other.id !== selfId && phoneKey(other.phone) === key).map((other) => ({ id: other.id, fullName: other.name }))
}

export function periodLabel(from: string, to: string) {
  const short = (iso: string) => iso.split('-').reverse().join('.')
  return `${short(from)} – ${short(to)}`
}
