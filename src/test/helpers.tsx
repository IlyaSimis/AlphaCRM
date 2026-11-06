import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, vi } from 'vitest'
import { LEBEDEVA_ID, PETROV_ID } from '../api/client'
import App from '../app/App'
import { pageTitles, serviceOf, serviceTitles, type Page } from '../app/pages'
import { ClinicThemeProvider } from '../app/theme'
import type { ServiceItem, StaffItem, StaffRole } from '../api/types'
import { roleLabels } from '../shared/roles'

/**
 * Боковое меню (десктопное). На «Сегодня» есть кнопки с теми же названиями, что пункты меню
 * («Касса»), поэтому переходы по меню ищутся здесь.
 */
export const menu = () => within(document.querySelector('.desktop-sidebar') as HTMLElement)

type User = ReturnType<typeof userEvent.setup>

function pageByTitle(title: string): Page {
  const page = (Object.keys(pageTitles) as Page[]).find((item) => pageTitles[item] === title)
  if (!page) throw new Error(`Нет страницы «${title}»`)
  return page
}

/** Открыть страницу: отдельный чат слева либо сервис и его раздел в верхней панели. */
export async function openPage(user: User, title: string) {
  if (title === 'Сообщения') {
    await user.click(menu().getByRole('button', { name: /^Сообщения(?:,|$)/ }))
  } else {
    await user.click(menu().getByRole('button', { name: serviceTitles[serviceOf(pageByTitle(title))] }))
    await user.click(screen.getByRole('tab', { name: title }))
  }
  // Нестартовые разделы подгружаются через React.lazy.
  await waitFor(() => expect(screen.queryByLabelText('Загрузка раздела')).not.toBeInTheDocument())
}

/** Есть ли страница у сотрудника: отдельный чат слева либо сервис и раздел сверху. */
export async function menuHas(user: User, title: string) {
  if (title === 'Сообщения') return menu().queryByRole('button', { name: /^Сообщения(?:,|$)/ }) !== null
  const service = menu().queryByRole('button', { name: serviceTitles[serviceOf(pageByTitle(title))] })
  if (!service) return false
  await user.click(service)
  return screen.queryByRole('tab', { name: title }) !== null
}

export const renderApp = () => render(<ClinicThemeProvider><App /></ClinicThemeProvider>)

export function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body }
}

/** Сервер недоступен: на странице входа доступна только песочница по ролям. */
export function goOffline() {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
}

export const anna = {
  id: '00000000-0000-0000-0000-000000000101',
  fullName: 'Анна Михайлова',
  birthDate: '1991-05-14',
  contacts: [{ type: 'PHONE', value: '+79160000000' }],
  consents: [{ revokedAt: null }],
}

export const TEST_CSRF = 'csrf-test-token'

/** Начальный администратор пустой базы в профиле local. */
export const ADMIN_PASSWORD = 'admin'
/** Пароль тестовых сотрудников, которых завёл администратор и которые уже сменили временный. */
export const STAFF_PASSWORD = 'test-password-1'

function account(id: string, fullName: string, username: string, role: StaffRole, practitionerId: string | null = null): StaffItem {
  return { id, fullName, username, role, practitionerId, active: true, mustChangePassword: false, clinicName: 'AlphaCRM' }
}

/**
 * Учётные записи фальшивого сервера. admin — как создаёт приложение на пустой базе; остальные —
 * тестовые сотрудники, которых администратор завёл через «Сотрудники». Это данные тестового
 * сервера, а не зашитые в приложение люди.
 */
export function testAccounts(): Record<string, StaffItem> {
  return {
    admin: account('00000000-0000-0000-0000-0000000a0001', 'Администратор', 'admin', 'ADMINISTRATOR'),
    't.manager': account('00000000-0000-0000-0000-0000000a0002', 'Тестовый менеджер', 't.manager', 'MANAGER'),
    't.cashier': account('00000000-0000-0000-0000-0000000a0003', 'Тестовый кассир', 't.cashier', 'CASHIER'),
    't.petrov': account('00000000-0000-0000-0000-0000000a0004', 'Александр Петров', 't.petrov', 'DOCTOR', PETROV_ID),
    't.lebedeva': account('00000000-0000-0000-0000-0000000a0005', 'Ольга Лебедева', 't.lebedeva', 'DOCTOR', LEBEDEVA_ID),
    't.nurse': account('00000000-0000-0000-0000-0000000a0006', 'Тестовая медсестра', 't.nurse', 'NURSE'),
    't.senior-nurse': account('00000000-0000-0000-0000-0000000a0008', 'Тестовый старший медперсонал', 't.senior-nurse', 'SENIOR_NURSE'),
    't.support': account('00000000-0000-0000-0000-0000000a0007', 'Тестовая техподдержка', 't.support', 'SUPPORT'),
  }
}

type Handler = (url: string, init?: RequestInit) => Promise<unknown> | unknown

/** Начальные услуги, как в Liquibase: их отдаёт GET /services фальшивого сервера. */
export const seedServices: ServiceItem[] = [
  { id: '00000000-0000-0000-0000-000000000601', name: 'Первичный приём', durationMinutes: 50, priceMinor: 450000, currency: 'RUB', active: true },
  { id: '00000000-0000-0000-0000-000000000602', name: 'Повторный приём', durationMinutes: 30, priceMinor: 300000, currency: 'RUB', active: true },
]

type AuthOptions = {
  /** Учётные записи с временным паролем: при входе сервер требует сменить пароль. */
  mustChange?: string[]
  /** Справочник услуг на GET /services; null — запрос уходит в handler. */
  services?: ServiceItem[] | null
  /** Ответ GET /me/dashboard; по умолчанию — без виджетов. */
  dashboard?: unknown
}

/**
 * Живой API с сессией: /auth/* отвечает как сервер (cookie XSRF-TOKEN, вход по паролю,
 * смена пароля), остальное — handler. Пока вход не выполнен, остальные запросы получают 401.
 * initial — кто уже вошёл; null — никто.
 */
export function withAuth(handler: Handler, initial: string | null = 'admin', options: AuthOptions = {}) {
  const accounts = testAccounts()
  const passwords: Record<string, string> = Object.fromEntries(Object.keys(accounts).map((name) => [name, name === 'admin' ? ADMIN_PASSWORD : STAFF_PASSWORD]))
  for (const name of options.mustChange ?? []) accounts[name] = { ...accounts[name], mustChangePassword: true }
  let current: string | null = initial
  const mock = vi.fn(async (url: string, init?: RequestInit) => {
    const address = String(url)
    const method = init?.method ?? 'GET'
    if (address.endsWith('/auth/csrf')) {
      document.cookie = `XSRF-TOKEN=${TEST_CSRF}; path=/`
      return jsonResponse(null, 204)
    }
    if (address.endsWith('/auth/me')) return current ? jsonResponse(accounts[current]) : jsonResponse({ code: 'UNAUTHORIZED' }, 401)
    if (address.endsWith('/auth/login') && method === 'POST') {
      const { username, password } = JSON.parse(String(init?.body))
      if (!accounts[username] || passwords[username] !== password) return jsonResponse({ code: 'INVALID_CREDENTIALS', message: 'Wrong username or password' }, 401)
      current = username
      return jsonResponse(accounts[username])
    }
    if (address.endsWith('/auth/logout')) {
      current = null
      return jsonResponse(null, 204)
    }
    if (!current) return jsonResponse({ code: 'UNAUTHORIZED' }, 401)
    if (address.endsWith('/auth/password') && method === 'POST') {
      const { currentPassword, newPassword } = JSON.parse(String(init?.body))
      if (passwords[current] !== currentPassword) return jsonResponse({ code: 'WRONG_PASSWORD', message: 'Current password is wrong' }, 409)
      passwords[current] = newPassword
      accounts[current] = { ...accounts[current], mustChangePassword: false }
      return jsonResponse(null, 204)
    }
    if (accounts[current].mustChangePassword) return jsonResponse({ code: 'PASSWORD_CHANGE_REQUIRED', message: 'Change the temporary password first' }, 403)
    // Дашборд по умолчанию пустой: тесты экранов, которым он не нужен, его не описывают.
    if (method === 'GET' && /\/me\/dashboard$/.test(address)) return jsonResponse(options.dashboard ?? { role: null, monthStart: '', today: '', generatedAt: '', widgets: [] })
    const services = options.services === undefined ? seedServices : options.services
    if (services && method === 'GET' && /\/services(\?|$)/.test(address)) return jsonResponse(services)
    return handler(address, init)
  })
  vi.stubGlobal('fetch', mock)
  return mock
}

/** Песочница без сервера: войти с ролью. Врач песочницы ведёт записи первого демо-врача. */
export async function enterDemo(user: User, role: StaffRole = 'DOCTOR') {
  await user.click(await screen.findByRole('button', { name: `Песочница: ${roleLabels[role]}` }))
}

/** Администратор, кассир и менеджер начинают со страницы «Сегодня»: перейти в расписание. */
export async function openSchedule(user: User) {
  await openPage(user, 'Расписание')
}

/** Вход через форму, как это делает сотрудник. */
export async function signIn(user: User, username: string, password = username === 'admin' ? ADMIN_PASSWORD : STAFF_PASSWORD) {
  const field = await screen.findByLabelText('Логин')
  await user.clear(field)
  await user.type(field, username)
  await user.type(screen.getByLabelText('Пароль'), password)
  await user.click(screen.getByRole('button', { name: 'Войти' }))
}

const loginsByRole: Record<StaffRole, string> = { OWNER: 'admin', SENIOR_ADMINISTRATOR: 'admin', ADMINISTRATOR: 'admin', DOCTOR: 't.petrov', MANAGER: 't.manager', CASHIER: 't.cashier', NURSE: 't.nurse', SENIOR_NURSE: 't.senior-nurse', SUPPORT: 't.support' }

/** Сменить сотрудника: выйти и войти другим — в песочнице кнопкой роли, на сервере через форму. */
export async function chooseRole(user: User, role: StaffRole, username = loginsByRole[role]) {
  await user.click(screen.getByRole('button', { name: 'Выйти' }))
  const sandbox = screen.queryAllByRole('button', { name: /^Песочница: / })
  if (sandbox.length > 0) {
    await enterDemo(user, role)
    return
  }
  await signIn(user, username)
}
