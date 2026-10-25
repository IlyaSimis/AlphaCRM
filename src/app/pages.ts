import { canManageClinic, canSeeAnalytics, canSeeCashier, canSeeRegistry, canSeeSignedDocuments, type Role } from '../shared/roles'

export type Page = 'schedule' | 'patients' | 'patient' | 'visit' | 'documents' | 'registry' | 'analytics' | 'audit' | 'cashier' | 'settings' | 'staff' | 'outbox'

export const pageTitles: Record<Page, string> = {
  schedule: 'Расписание',
  patients: 'Пациенты',
  patient: 'Карточка пациента',
  visit: 'Приём пациента',
  documents: 'Подписанные документы',
  registry: 'Регистратура',
  analytics: 'Аналитика',
  audit: 'Журнал аудита',
  cashier: 'Касса',
  settings: 'График и цены',
  staff: 'Сотрудники',
  outbox: 'Внешние выводы',
}

/** Страницы, которые роль видит в меню. Карточка и приём открываются из других страниц. */
export function navigationFor(role: Role): Page[] {
  return [
    'schedule',
    'patients',
    ...(canSeeSignedDocuments(role) ? (['documents'] as Page[]) : []),
    ...(canSeeRegistry(role) ? (['registry'] as Page[]) : []),
    ...(canSeeCashier(role) ? (['cashier'] as Page[]) : []),
    ...(canSeeAnalytics(role) ? (['analytics', 'audit'] as Page[]) : []),
    ...(canManageClinic(role) ? (['settings', 'staff', 'outbox'] as Page[]) : []),
  ]
}

export function canOpen(page: Page, role: Role) {
  if (page === 'visit') return role === 'DOCTOR'
  if (page === 'patient') return true
  return navigationFor(role).includes(page)
}
