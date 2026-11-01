import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { IntegrationType } from '../../api/integrations'
import { enterDemo, goOffline, jsonResponse, menuHas, openPage, renderApp, withAuth } from '../../test/helpers'

function integration(type: IntegrationType, body: Record<string, unknown> = {}, hasSecret = false) {
  return {
    id: `00000000-0000-0000-0000-000000000${type === 'MANGO' ? '901' : type === 'TELEGRAM' ? '902' : '903'}`,
    type,
    status: 'PENDING',
    publicKey: '00000000-0000-0000-0000-000000000aaa',
    settings: body,
    hasSecret,
    secretVersion: hasSecret ? 1 : null,
  }
}

describe('clinic integrations', () => {
  it('saves Mango settings and write-only secrets from the administration page', async () => {
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/integrations') && method === 'GET') return jsonResponse([])
      if (address.includes('/integrations/mango/settings') && method === 'PUT') return jsonResponse(integration('MANGO', JSON.parse(String(init?.body))))
      if (address.includes('/integrations/mango/secret') && method === 'PUT') return jsonResponse(integration('MANGO', { apiUrl: 'https://app.mango-office.ru' }, true))
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Интеграции')
    expect(await screen.findByRole('region', { name: 'Интеграция Mango' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Интеграция Telegram' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Интеграция MAX' })).toBeInTheDocument()

    await user.type(screen.getByLabelText('Mango API key'), 'clinic-key')
    await user.type(screen.getByLabelText('Mango API salt'), 'clinic-salt')
    await user.click(screen.getByRole('button', { name: 'Сохранить Mango' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes('/integrations/mango/secret') && init?.method === 'PUT')).toBe(true))
    const secret = fetchMock.mock.calls.find(([url]) => String(url).includes('/integrations/mango/secret'))
    expect(JSON.parse(String(secret?.[1]?.body))).toEqual({ apiKey: 'clinic-key', apiSalt: 'clinic-salt' })
    expect(await screen.findByText('Секрет задан · версия 1')).toBeInTheDocument()
    expect(screen.getByLabelText('Mango API key')).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Копировать call webhook' })).toBeInTheDocument()
  })

  it('saves Telegram and MAX bot tokens independently', async () => {
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/integrations') && method === 'GET') return jsonResponse([])
      for (const [path, type] of [['telegram', 'TELEGRAM'], ['max', 'MAX']] as const) {
        if (address.includes(`/integrations/${path}/settings`) && method === 'PUT') return jsonResponse(integration(type, JSON.parse(String(init?.body))))
        if (address.includes(`/integrations/${path}/secret`) && method === 'PUT') return jsonResponse(integration(type, { apiUrl: type === 'MAX' ? 'https://platform-api2.max.ru' : 'https://api.telegram.org' }, true))
      }
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Интеграции')
    await user.type(await screen.findByLabelText('Telegram Bot Token'), 'telegram-token')
    await user.type(screen.getByLabelText('Telegram webhook secret'), 'telegram-webhook-secret')
    await user.click(screen.getByRole('button', { name: 'Сохранить Telegram' }))
    await user.type(screen.getByLabelText('MAX Bot Token'), 'max-token')
    await user.type(screen.getByLabelText('MAX webhook secret'), 'max-webhook-secret')
    await user.click(screen.getByRole('button', { name: 'Сохранить MAX' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/integrations/max/secret'))).toBe(true))
    const telegram = fetchMock.mock.calls.find(([url]) => String(url).includes('/integrations/telegram/secret'))
    const max = fetchMock.mock.calls.find(([url]) => String(url).includes('/integrations/max/secret'))
    expect(JSON.parse(String(telegram?.[1]?.body))).toEqual({ botToken: 'telegram-token', relaySecret: '', webhookSecret: 'telegram-webhook-secret' })
    expect(JSON.parse(String(max?.[1]?.body))).toEqual({ botToken: 'max-token', relaySecret: '', webhookSecret: 'max-webhook-secret' })
    expect(screen.getAllByRole('button', { name: 'Копировать webhook' })).toHaveLength(2)
  })

  it('does not expose integrations to roles without permission', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'CASHIER')
    expect(await menuHas(user, 'Интеграции')).toBe(false)
  })
})
