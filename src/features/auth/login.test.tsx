import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { anna, enterDemo, jsonResponse, menu, renderApp, signIn, TEST_CSRF, withAuth } from '../../test/helpers'

const api = (address: string) => {
  if (address.includes('/patients')) return jsonResponse([anna])
  if (address.includes('/appointments')) return jsonResponse([])
  return jsonResponse({}, 404)
}

describe('staff sign-in', () => {
  it('before sign-in only the sign-in page is shown; a wrong password gets a generic refusal', async () => {
    const fetchMock = withAuth(api, null)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByLabelText('Логин')).toBeInTheDocument()
    expect(screen.getByText('AlphaCRM')).toBeInTheDocument()
    expect(screen.queryByText('Тихая клиника')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Расписание' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Песочница: / })).not.toBeInTheDocument()

    await signIn(user, 'admin', 'wrong-password')
    expect(await screen.findByText('Неверный логин или пароль')).toBeInTheDocument()
    await signIn(user, 'nobody', 'admin')
    expect(await screen.findByText('Неверный логин или пароль')).toBeInTheDocument()
    expect(screen.getByLabelText('Пароль')).toHaveValue('')

    const login = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/auth/login'))
    expect(login?.[1]?.credentials).toBe('include')
    expect(login?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': TEST_CSRF })
  })

  it('signs in, shows the staff member without a role switch and signs out', async () => {
    const fetchMock = withAuth(api, null)
    const user = userEvent.setup()
    renderApp()
    // admin / admin — начальный администратор локального профиля, без смены пароля.
    await signIn(user, 'admin', 'admin')
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(screen.getAllByText('Администратор', { selector: '.profile *' }).length).toBeGreaterThan(0)
    // Роль под именем — название, а не код API.
    expect(screen.queryByText('ADMINISTRATOR')).not.toBeInTheDocument()
    expect(screen.getByText('AlphaCRM', { selector: '.topbar *' })).toBeInTheDocument()
    expect(screen.queryByText(/временным паролем/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Сотрудники' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Роль пользователя')).not.toBeInTheDocument()
    expect(screen.queryByText(/Демо-песочница/)).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    expect(await screen.findByLabelText('Логин')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/auth/logout') && init?.method === 'POST')).toBe(true)
  })

  it('a 401 from the live API returns to the sign-in page', async () => {
    withAuth((address) => (address.includes('/appointments') ? jsonResponse({ code: 'UNAUTHORIZED' }, 401) : api(address)))
    renderApp()
    expect(await screen.findByText('Сессия закончилась. Войдите снова.')).toBeInTheDocument()
    expect(screen.getByLabelText('Логин')).toBeInTheDocument()
  })

  it('without a server opens a per-role sandbox with no people and no requests on behalf of a role', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('offline'))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText(/Сервер недоступен/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Демо: / })).not.toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /^Песочница: / }).map((button) => button.textContent)).toEqual(['Песочница: Администратор', 'Песочница: Врач', 'Песочница: Менеджер', 'Песочница: Кассир'])
    await enterDemo(user, 'CASHIER')
    expect(screen.getByText('Кассир песочницы')).toBeInTheDocument()
    expect(screen.getByText('Демо-песочница: данные не сохраняются')).toBeInTheDocument()
    expect(menu().getByRole('button', { name: 'Касса' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Сменить пароль' })).not.toBeInTheDocument()
    await user.click(menu().getByRole('button', { name: 'Касса' }))
    expect(fetchMock.mock.calls.every(([url]) => String(url).endsWith('/auth/me'))).toBe(true)
  })

  it('changes own password: needs the current one and a new one of at least 8 characters', async () => {
    const fetchMock = withAuth(api, 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))
    await user.type(screen.getByLabelText('Текущий пароль'), 'test-password-1')
    await user.type(screen.getByLabelText('Новый пароль'), 'short')
    await user.type(screen.getByLabelText('Повторите пароль'), 'short')
    await user.click(screen.getByRole('button', { name: 'Сохранить пароль' }))
    expect(screen.getByText('Пароль должен быть не короче 8 символов')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/password'))).toBe(false)

    await user.clear(screen.getByLabelText('Новый пароль'))
    await user.type(screen.getByLabelText('Новый пароль'), 'new-password-1')
    await user.clear(screen.getByLabelText('Повторите пароль'))
    await user.type(screen.getByLabelText('Повторите пароль'), 'new-password-1')
    await user.click(screen.getByRole('button', { name: 'Сохранить пароль' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/auth/password'))
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ currentPassword: 'test-password-1', newPassword: 'new-password-1' })
  })
})
