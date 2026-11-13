import { canManagePrices, canManageSchedule, canSeeAnalytics, canSeeAudit, canSeeCashier, canSeeDrive, canSeeOutbox, canSeePatients, canSeeRegistry, canSeeSignedDocuments, canSeeStaff, canWriteClinical, type Role } from '../shared/roles'
import { can } from '../shared/permissions'

export type Page =
  | 'today' | 'workspace' | 'messages' | 'analytics' | 'hr-me' | 'staff' | 'hr' | 'audit' | 'break-glass' | 'outbox' | 'integrations'
  | 'schedule' | 'patients' | 'patient' | 'visit' | 'registry' | 'documents' | 'contract-templates' | 'hours'
  | 'stock-balances' | 'stock-items' | 'stock-documents' | 'stock-marking' | 'stock-norms'
  | 'cashier' | 'sales' | 'prices'
  | 'drive' | 'drive-shared' | 'drive-trash'

/** Сервис — вкладка верхней панели; его разделы — в боковом меню. */
export type Service = 'dashboards' | 'staff-chat' | 'management' | 'patients' | 'documents' | 'inventory' | 'finance' | 'drive'

export const serviceTitles: Record<Service, string> = {
  dashboards: 'Мое пространство',
  'staff-chat': 'StaffChat',
  management: 'Управление',
  patients: 'База данных пациентов',
  documents: 'Документы',
  inventory: 'Склад',
  finance: 'Финансы',
  drive: 'Диск',
}

export const services: Service[] = ['dashboards', 'staff-chat', 'management', 'patients', 'documents', 'inventory', 'finance', 'drive']

export const pageTitles: Record<Page, string> = {
  today: 'Сегодня',
  workspace: 'Дашборды',
  messages: 'StaffChat',
  analytics: 'Аналитика',
  staff: 'Сотрудники',
  hr: 'Ставки и условия',
  'hr-me': 'Мои условия работы',
  audit: 'Журнал аудита',
  'break-glass': 'Экстренные доступы',
  outbox: 'Внешние выводы',
  integrations: 'Интеграции',
  schedule: 'Расписание',
  patients: 'Пациенты',
  patient: 'Карточка пациента',
  visit: 'Приём пациента',
  registry: 'Регистратура',
  documents: 'Подписанные документы',
  'contract-templates': 'Шаблоны договоров',
  hours: 'График врачей',
  'stock-balances': 'Остатки',
  'stock-items': 'Номенклатура',
  'stock-documents': 'Накладные',
  'stock-marking': 'Маркировка',
  'stock-norms': 'Нормы расхода',
  cashier: 'Касса',
  sales: 'Продажи и выручка',
  prices: 'Услуги и цены',
  drive: 'Мой диск',
  'drive-shared': 'Доступные мне',
  'drive-trash': 'Корзина',
}

/** Разделы сервиса в боковом меню, по группам. Карточка пациента и приём в меню не стоят. */
export const serviceSections: Record<Service, Array<{ label: string; pages: Page[] }>> = {
  dashboards: [
    { label: 'ОБЗОР', pages: ['today', 'workspace', 'analytics', 'hr-me'] },
  ],
  'staff-chat': [
    { label: 'ВНУТРЕННИЙ ЧАТ', pages: ['messages'] },
  ],
  management: [
    { label: 'УПРАВЛЕНИЕ', pages: ['staff', 'hours', 'hr', 'audit', 'break-glass', 'integrations'] },
  ],
  patients: [
    { label: 'РАБОТА С ПАЦИЕНТАМИ', pages: ['schedule', 'patients', 'registry', 'documents'] },
  ],
  documents: [
    { label: 'ДОГОВОРЫ', pages: ['contract-templates'] },
  ],
  inventory: [
    { label: 'СКЛАД', pages: ['stock-balances', 'stock-items', 'stock-documents'] },
    { label: 'УЧЁТ', pages: ['stock-marking', 'stock-norms'] },
  ],
  finance: [
    { label: 'ФИНАНСЫ', pages: ['cashier', 'sales', 'prices'] },
  ],
  drive: [
    { label: 'КОРПОРАТИВНЫЙ ДИСК', pages: ['drive', 'drive-shared', 'drive-trash'] },
  ],
}

/** Сервис страницы: по нему подсвечивается вкладка сверху и строится боковое меню. */
export function serviceOf(page: Page): Service {
  if (page === 'patient' || page === 'visit') return 'patients'
  return services.find((service) => serviceSections[service].some((section) => section.pages.includes(page))) ?? 'patients'
}

/** Может ли роль открыть страницу. */
function allowed(page: Page, role: Role): boolean {
  switch (page) {
    case 'today': return hasTodayPage(role)
    case 'workspace': return can(role, 'workspace.item.use')
    case 'messages': return true
    case 'analytics': return canSeeAnalytics(role)
    case 'staff': return canSeeStaff(role)
    case 'hr': return can(role, 'identity.hr.manage')
    case 'hr-me': return can(role, 'identity.hr.read-own')
    case 'audit': return canSeeAudit(role)
    case 'break-glass': return can(role, 'clinical.break-glass.review')
    case 'outbox': return canSeeOutbox(role)
    case 'integrations': return can(role, 'integration.configuration.read')
    case 'schedule': return true
    case 'patients':
    case 'patient': return canSeePatients(role)
    case 'visit': return canWriteClinical(role)
    case 'registry': return canSeeRegistry(role)
    case 'documents': return canSeeSignedDocuments(role)
    case 'contract-templates': return can(role, 'clinical.contract.use')
    case 'hours': return can(role, 'schedule.practitioner.manage')
    case 'stock-balances': return can(role, 'inventory.stock.read')
    case 'stock-items': return can(role, 'inventory.item.read')
    case 'stock-documents': return can(role, 'inventory.document.write')
    case 'stock-marking': return can(role, 'inventory.marking.read')
    case 'stock-norms': return can(role, 'schedule.service.manage', 'inventory.item.write')
    case 'cashier': return canSeeCashier(role)
    case 'sales': return can(role, 'finance.revenue.read')
    case 'prices': return canManageSchedule(role) || canManagePrices(role)
    case 'drive':
    case 'drive-shared':
    case 'drive-trash': return canSeeDrive(role)
  }
}

/** Разделы сервиса, доступные роли, — для бокового меню. Пустые группы не показываются. */
export function sectionsFor(service: Service, role: Role) {
  return serviceSections[service]
    .map((section) => ({ label: section.label, pages: section.pages.filter((page) => allowed(page, role)) }))
    .filter((section) => section.pages.length > 0)
}

/** Сервисы левого меню, в которых у роли есть хотя бы один раздел. */
export function servicesFor(role: Role): Service[] {
  return services.filter((service) => sectionsFor(service, role).length > 0)
}

/** Все страницы меню роли; StaffChat — самостоятельный сервис левого меню. */
export function navigationFor(role: Role): Page[] {
  return servicesFor(role).flatMap((service) => sectionsFor(service, role).flatMap((section) => section.pages))
}

/** Первая страница сервиса для роли: на неё ведёт вкладка верхней панели. */
export function firstPageOf(service: Service, role: Role): Page | null {
  return sectionsFor(service, role)[0]?.pages[0] ?? null
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
  return allowed(page, role)
}

/** Страницы склада и диска — одна страница с разделом. */
export const stockSection = (page: Page) => (page.startsWith('stock-') ? (page.slice(6) as 'balances' | 'items' | 'documents' | 'marking' | 'norms') : null)
export const driveView = (page: Page) => (page === 'drive' ? 'mine' : page === 'drive-shared' ? 'shared' : page === 'drive-trash' ? 'trash' : null)
