import { canSeeAnalytics, canSeeCashier, type Role } from '../shared/roles'

export type Page = 'schedule' | 'patients' | 'patient' | 'visit' | 'analytics' | 'audit' | 'cashier'

export const pageTitles: Record<Page, string> = {
  schedule: 'Расписание',
  patients: 'Пациенты',
  patient: 'Карточка пациента',
  visit: 'Приём пациента',
  analytics: 'Аналитика',
  audit: 'Журнал аудита',
  cashier: 'Касса',
}

/** Страницы, которые роль видит в меню. Карточка и приём открываются из других страниц. */
export function navigationFor(role: Role): Page[] {
  return [
    'schedule',
    'patients',
    ...(canSeeCashier(role) ? (['cashier'] as Page[]) : []),
    ...(canSeeAnalytics(role) ? (['analytics', 'audit'] as Page[]) : []),
  ]
}

export function canOpen(page: Page, role: Role) {
  if (page === 'visit') return role === 'Врач'
  if (page === 'patient') return true
  return navigationFor(role).includes(page)
}
