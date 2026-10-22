import { ACTORS, LEBEDEVA_ID, PETROV_ID, type ApiRole } from '../api/client'

export type Role = 'Администратор' | 'Врач' | 'Менеджер' | 'Кассир'

export const roles: Role[] = ['Врач', 'Администратор', 'Менеджер', 'Кассир']

export const apiRoles: Record<Role, ApiRole> = { Администратор: 'ADMINISTRATOR', Врач: 'DOCTOR', Менеджер: 'MANAGER', Кассир: 'CASHIER' }

/** Врачи, от имени которых может работать роль «Врач». */
export const doctors = [
  { id: PETROV_ID, name: 'Александр Петров', initials: 'АП' },
  { id: LEBEDEVA_ID, name: 'Ольга Лебедева', initials: 'ОЛ' },
]

const staff: Record<Exclude<Role, 'Врач'>, { name: string; initials: string }> = {
  Администратор: { name: ACTORS.ADMINISTRATOR.name, initials: 'ЕС' },
  Менеджер: { name: ACTORS.MANAGER.name, initials: 'ИК' },
  Кассир: { name: ACTORS.CASHIER.name, initials: 'НО' },
}

export function profileFor(role: Role, doctorId: string) {
  if (role === 'Врач') return doctors.find((doctor) => doctor.id === doctorId) ?? doctors[0]
  return staff[role]
}

/** Разбирает сохранённую роль; «Регистратор» — старое название администратора. */
export function parseRole(value: string | null): Role {
  if (value === 'Менеджер' || value === 'Кассир') return value
  return value === 'Администратор' || value === 'Регистратор' ? 'Администратор' : 'Врач'
}

export function parseDoctor(value: string | null) {
  return doctors.some((doctor) => doctor.id === value) ? (value as string) : PETROV_ID
}

/** Аналитика и журнал — администратору и менеджеру; касса — кассиру и администратору. */
export const canSeeAnalytics = (role: Role) => role === 'Администратор' || role === 'Менеджер'
export const canSeeCashier = (role: Role) => role === 'Кассир' || role === 'Администратор'
