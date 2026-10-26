import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, chooseRole, enterDemo, goOffline, jsonResponse, menu, renderApp, TEST_CSRF, withAuth } from '../../test/helpers'

describe('cashier', () => {
  it('visible to cashier and administrator, not to doctor and manager', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(menu().queryByRole('button', { name: 'Касса' })).not.toBeInTheDocument()
    await chooseRole(user, 'MANAGER')
    expect(menu().queryByRole('button', { name: 'Касса' })).not.toBeInTheDocument()
    await chooseRole(user, 'CASHIER')
    expect(menu().getByRole('button', { name: 'Касса' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Аналитика' })).not.toBeInTheDocument()
    await chooseRole(user, 'ADMINISTRATOR')
    expect(menu().getByRole('button', { name: 'Касса' })).toBeInTheDocument()
  })

  it('the administrator issues a charge, the cashier takes the payment', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(menu().getByRole('button', { name: 'Касса' }))
    expect(screen.getAllByText('Счёт не выставлен').length).toBeGreaterThan(0)
    expect(screen.queryByText(/Диагноз/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Выставить счёт Мария Соколова 09:00' }))
    expect(await screen.findByText('Ожидает оплаты')).toBeInTheDocument()

    await chooseRole(user, 'CASHIER')
    await user.click(menu().getByRole('button', { name: 'Касса' }))
    expect(screen.queryByRole('button', { name: /выставить счёт/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Принять оплату Мария Соколова 09:00' }))
    expect(await screen.findByText('Оплачен')).toBeInTheDocument()
  })

  it('the administrator issues all missing charges at once', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(menu().getByRole('button', { name: 'Касса' }))
    await user.click(screen.getByRole('button', { name: /^Выставить все счета/ }))
    expect(await screen.findByText(/выставлено счетов/i)).toBeInTheDocument()
    expect(screen.queryByText('Счёт не выставлен')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Выставить все счета/ })).not.toBeInTheDocument()
  })

  it('a cashier pays a server charge under their own name', async () => {
    const appointmentId = '00000000-0000-0000-0000-000000000502'
    const fetchMock = withAuth(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/payments')) return jsonResponse({ chargeStatus: 'PAID' })
      if (address.includes('/billing/charges')) return jsonResponse([{ chargeId: 'c1', appointmentId, amountMinor: 300000, currency: 'RUB', status: init?.method === 'POST' ? 'PAID' : 'PENDING', createdAt: '2026-10-06T10:40:00Z' }])
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('status=COMPLETED')) return jsonResponse([{ id: appointmentId, patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-06T10:10:00Z', endsAt: '2026-10-06T10:40:00Z', serviceName: 'Повторный приём', status: 'COMPLETED', amountMinor: 300000, currency: 'RUB' }])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 't.cashier')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(menu().getByRole('button', { name: 'Касса' }))
    expect(await screen.findByText('Ожидает оплаты')).toBeInTheDocument()
    expect(screen.getByText('Анна Михайлова')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Принять оплату Анна Михайлова 10:10' }))
    const payment = fetchMock.mock.calls.find(([url]) => String(url).includes('/payments'))
    // Автора платежа сервер берёт из сессии: роль и id в запрос не подставляются.
    expect(payment?.[1]?.credentials).toBe('include')
    expect(payment?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': TEST_CSRF })
    expect(payment?.[1]?.headers).not.toHaveProperty('X-Demo-Role')
    expect(payment?.[1]?.headers).not.toHaveProperty('X-Actor-Id')
    expect(JSON.parse(String(payment?.[1]?.body))).toEqual({ idempotencyKey: 'pay-c1' })
  })
})
