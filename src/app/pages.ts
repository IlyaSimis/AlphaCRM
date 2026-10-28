import { canManageSchedule, canSeeAnalytics, canSeeAudit, canSeeCashier, canSeeDrive, canSeeInventory, canSeeOutbox, canSeePatients, canSeeRegistry, canSeeSignedDocuments, canSeeStaff, canWriteClinical, type Role } from '../shared/roles'
import { can } from '../shared/permissions'

export type Page = 'today' | 'schedule' | 'patients' | 'patient' | 'visit' | 'documents' | 'registry' | 'analytics' | 'audit' | 'cashier' | 'settings' | 'staff' | 'outbox' | 'inventory' | 'drive'

export const pageTitles: Record<Page, string> = {
  today: 'Сегодня',
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
  inventory: 'Склад',
  drive: 'Диск',
}

/** Страницы, которые роль видит в меню. Карточка и приём открываются из других страниц. */
export function navigationFor(role: Role): Page[] {
  return [
    ...(hasTodayPage(role) ? (['today'] as Page[]) : []),
    'schedule',
    ...(canSeePatients(role) ? (['patients'] as Page[]) : []),
    ...(canSeeSignedDocuments(role) ? (['documents'] as Page[]) : []),
    ...(canSeeRegistry(role) ? (['registry'] as Page[]) : []),
    ...(canSeeCashier(role) ? (['cashier'] as Page[]) : []),
    ...(canSeeInventory(role) ? (['inventory'] as Page[]) : []),
    ...(canSeeDrive(role) ? (['drive'] as Page[]) : []),
    ...(canSeeAnalytics(role) ? (['analytics'] as Page[]) : []),
    ...(canSeeAudit(role) ? (['audit'] as Page[]) : []),
    ...(canManageSchedule(role) ? (['settings'] as Page[]) : []),
    ...(canSeeStaff(role) ? (['staff'] as Page[]) : []),
    ...(canSeeOutbox(role) ? (['outbox'] as Page[]) : []),
  ]
}

/**
 * «Сегодня» с персональным дашбордом — первая страница после входа. Врач начинает день с
 * расписания: его дашборд (свои приёмы за месяц, ближайшие, неподписанные документы) — над ним.
 */
export const hasTodayPage = (role: Role) => can(role, 'insights.dashboard.read') && !canWriteClinical(role)

/** Первая страница после входа. */
export function startPage(role: Role): Page {
  return hasTodayPage(role) ? 'today' : 'schedule'
}

export function canOpen(page: Page, role: Role) {
  if (page === 'visit') return canWriteClinical(role)
  if (page === 'patient') return canSeePatients(role)
  return navigationFor(role).includes(page)
}
