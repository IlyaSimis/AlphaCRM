import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { anna, chooseRole, goOffline, jsonResponse, renderApp } from '../../test/helpers'

describe('касса', () => {
  it('видна кассиру и администратору, но не врачу и менеджеру', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    expect(screen.queryByRole('button', { name: 'Касса' })).not.toBeInTheDocument()
    await chooseRole(user, 'Менеджер')
    expect(screen.queryByRole('button', { name: 'Касса' })).not.toBeInTheDocument()
    await chooseRole(user, 'Кассир')
    expect(screen.getByRole('button', { name: 'Касса' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Аналитика' })).not.toBeInTheDocument()
    await chooseRole(user, 'Администратор')
    expect(screen.getByRole('button', { name: 'Касса' })).toBeInTheDocument()
  })

  it('администратор выставляет счёт, кассир принимает оплату', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await chooseRole(user, 'Администратор')
    await user.click(screen.getByRole('button', { name: 'Касса' }))
    expect(screen.getAllByText('Счёт не выставлен').length).toBeGreaterThan(0)
    expect(screen.queryByText(/Диагноз/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Выставить счёт Мария Соколова 09:00' }))
    expect(await screen.findByText('Ожидает оплаты')).toBeInTheDocument()

    await chooseRole(user, 'Кассир')
    await user.click(screen.getByRole('button', { name: 'Касса' }))
    expect(screen.queryByRole('button', { name: /выставить счёт/i })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Принять оплату Мария Соколова 09:00' }))
    expect(await screen.findByText('Оплачен')).toBeInTheDocument()
  })

  it('кассир оплачивает счёт с сервера от своего имени', async () => {
    const appointmentId = '00000000-0000-0000-0000-000000000502'
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/payments')) return jsonResponse({ chargeStatus: 'PAID' })
      if (address.includes('/billing/charges')) return jsonResponse([{ chargeId: 'c1', appointmentId, amountMinor: 300000, currency: 'RUB', status: init?.method === 'POST' ? 'PAID' : 'PENDING', createdAt: '2026-10-06T10:40:00Z' }])
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('status=COMPLETED')) return jsonResponse([{ id: appointmentId, patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-06T10:10:00Z', endsAt: '2026-10-06T10:40:00Z', serviceName: 'Повторный приём', status: 'COMPLETED', amountMinor: 300000, currency: 'RUB' }])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await chooseRole(user, 'Кассир')
    await user.click(screen.getByRole('button', { name: 'Касса' }))
    expect(await screen.findByText('Ожидает оплаты')).toBeInTheDocument()
    expect(screen.getByText('Анна Михайлова')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Принять оплату Анна Михайлова 10:10' }))
    const payment = fetchMock.mock.calls.find(([url]) => String(url).includes('/payments'))
    expect(payment?.[1]?.headers).toMatchObject({ 'X-Demo-Role': 'CASHIER', 'X-Actor-Id': '00000000-0000-0000-0000-000000000903' })
    expect(JSON.parse(String(payment?.[1]?.body))).toEqual({ idempotencyKey: 'pay-c1' })
  })
})
