import { screen, waitFor, within } from '@testing-library/react'
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
      if (address.endsWith('/clinic/notification-settings') && method === 'GET') return jsonResponse({ reminder24hEnabled: true, reminder3hEnabled: false })
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
      if (address.endsWith('/clinic/notification-settings') && method === 'GET') return jsonResponse({ reminder24hEnabled: true, reminder3hEnabled: false })
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
    await user.type(screen.getAllByLabelText('Публичный HTTPS-адрес CRM')[0], 'https://crm.example.test')
    await user.type(screen.getByLabelText('Telegram webhook secret'), 'telegram-webhook-secret')
    await user.click(screen.getByRole('button', { name: 'Сохранить Telegram' }))
    await user.type(screen.getByLabelText('MAX Bot Token'), 'max-token')
    await user.type(screen.getByLabelText('MAX webhook secret'), 'max-webhook-secret')
    await user.click(screen.getByRole('button', { name: 'Сохранить MAX' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/integrations/max/secret'))).toBe(true))
    const telegramSettings = fetchMock.mock.calls.find(([url]) => String(url).includes('/integrations/telegram/settings'))
    const telegram = fetchMock.mock.calls.find(([url]) => String(url).includes('/integrations/telegram/secret'))
    const max = fetchMock.mock.calls.find(([url]) => String(url).includes('/integrations/max/secret'))
    expect(JSON.parse(String(telegramSettings?.[1]?.body))).toMatchObject({ webhookBaseUrl: 'https://crm.example.test' })
    expect(JSON.parse(String(telegram?.[1]?.body))).toEqual({ botToken: 'telegram-token', relaySecret: '', webhookSecret: 'telegram-webhook-secret' })
    expect(JSON.parse(String(max?.[1]?.body))).toEqual({ botToken: 'max-token', relaySecret: '', webhookSecret: 'max-webhook-secret' })
    expect(screen.getAllByRole('button', { name: 'Копировать webhook' })).toHaveLength(2)
  })

  it('connects a provider webhook only on demand and displays its current status', async () => {
    const active = (type: IntegrationType) => ({
      ...integration(type, { webhookBaseUrl: 'https://crm.example.test' }, true),
      status: 'ACTIVE',
    })
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/clinic/notification-settings') && method === 'GET') return jsonResponse({ reminder24hEnabled: true, reminder3hEnabled: false })
      if (address.endsWith('/integrations') && method === 'GET') return jsonResponse([active('TELEGRAM'), active('MAX')])
      if (address.includes('/integrations/telegram/webhook/connect') && method === 'POST') {
        return jsonResponse({ state: 'CONNECTED', url: 'https://crm.example.test/api/v1/integrations/telegram/events/key', detail: null, pendingUpdates: 2 })
      }
      if (address.includes('/integrations/telegram/webhook/status') && method === 'GET') {
        return jsonResponse({ state: 'ERROR', url: 'https://crm.example.test/api/v1/integrations/telegram/events/key', detail: 'Connection refused', pendingUpdates: 4 })
      }
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Интеграции')
    const telegram = await screen.findByRole('region', { name: 'Интеграция Telegram' })
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/webhook/'))).toBe(false)

    await user.click(within(telegram).getByRole('button', { name: 'Подключить webhook' }))
    expect(await within(telegram).findByText('Webhook: подключён')).toBeInTheDocument()
    expect(within(telegram).getByText('Ожидают доставки: 2')).toBeInTheDocument()
    await user.click(within(telegram).getByRole('button', { name: 'Проверить' }))
    expect(await within(telegram).findByText('Webhook: ошибка')).toBeInTheDocument()
    expect(within(telegram).getByText('Connection refused')).toBeInTheDocument()
    expect(within(telegram).getByText('Ожидают доставки: 4')).toBeInTheDocument()
    expect(within(screen.getByRole('region', { name: 'Интеграция MAX' })).getByRole('button', { name: 'Подключить webhook' })).toBeEnabled()
  })

  it('saves both automatic reminder switches', async () => {
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/clinic/notification-settings') && method === 'GET') return jsonResponse({ reminder24hEnabled: true, reminder3hEnabled: false })
      if (address.endsWith('/clinic/notification-settings') && method === 'PUT') return jsonResponse(JSON.parse(String(init?.body)))
      if (address.endsWith('/integrations') && method === 'GET') return jsonResponse([])
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Интеграции')
    const panel = await screen.findByRole('region', { name: 'Напоминания пациентам' })
    const daily = within(panel).getByRole('switch', { name: 'За 24 часа' })
    const short = within(panel).getByRole('switch', { name: 'За 3 часа' })
    expect(daily).toBeChecked()
    expect(short).not.toBeChecked()

    await user.click(daily)
    await user.click(short)
    await user.click(within(panel).getByRole('button', { name: 'Сохранить напоминания' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/clinic/notification-settings') && init?.method === 'PUT')).toBe(true))
    const saved = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/clinic/notification-settings') && init?.method === 'PUT')
    expect(JSON.parse(String(saved?.[1]?.body))).toEqual({ reminder24hEnabled: false, reminder3hEnabled: true })
  })

  it('saves an offline goal and uploads attributed conversions without messenger webhook fields', async () => {
    let pending = 1
    let uploaded = 0
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/clinic/notification-settings') && method === 'GET') return jsonResponse({ reminder24hEnabled: true, reminder3hEnabled: false })
      if (address.endsWith('/integrations') && method === 'GET') return jsonResponse([])
      if (address.includes('/integrations/yandex_metrica/offline-conversions/upload') && method === 'POST') {
        uploaded += pending
        pending = 0
        return jsonResponse({ uploaded, uploadingId: 'upload-1' })
      }
      if (address.includes('/integrations/yandex_metrica/offline-conversions') && method === 'GET') return jsonResponse({ configured: true, attributed: 1, pending, uploading: 0, uploaded, failed: 0, lastUploadedAt: null, lastError: null })
      if (address.includes('/integrations/yandex_metrica/settings') && method === 'PUT') return jsonResponse(integration('YANDEX_METRICA', JSON.parse(String(init?.body))))
      if (address.includes('/integrations/yandex_metrica/secret') && method === 'PUT') return jsonResponse(integration('YANDEX_METRICA', { apiUrl: 'https://api-metrika.yandex.net', counterId: '12345678', offlineGoalId: '987654' }, true))
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Интеграции')
    const metrica = await screen.findByRole('region', { name: 'Интеграция Яндекс Метрика' })
    await user.type(within(metrica).getByLabelText('ID счётчика'), '12345678')
    await user.type(within(metrica).getByLabelText('ID offline-цели'), '987654')
    await user.type(within(metrica).getByLabelText('Яндекс OAuth-токен'), 'metrica-secret')
    expect(within(metrica).queryByLabelText('Публичный HTTPS-адрес CRM')).not.toBeInTheDocument()
    expect(within(metrica).queryByText(/Webhook для кабинета/)).not.toBeInTheDocument()
    await user.click(within(metrica).getByRole('button', { name: 'Сохранить Яндекс Метрика' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/integrations/yandex_metrica/secret'))).toBe(true))
    const settings = fetchMock.mock.calls.find(([url]) => String(url).includes('/integrations/yandex_metrica/settings'))
    const secret = fetchMock.mock.calls.find(([url]) => String(url).includes('/integrations/yandex_metrica/secret'))
    expect(JSON.parse(String(settings?.[1]?.body))).toEqual({ apiUrl: 'https://api-metrika.yandex.net', counterId: '12345678', offlineGoalId: '987654' })
    expect(JSON.parse(String(secret?.[1]?.body))).toEqual({ oauthToken: 'metrica-secret' })

    const savedMetrica = await screen.findByRole('region', { name: 'Интеграция Яндекс Метрика' })
    expect(await within(savedMetrica).findByText('К выгрузке: 1')).toBeInTheDocument()
    await user.click(within(savedMetrica).getByRole('button', { name: 'Выгрузить конверсии' }))
    expect(await within(savedMetrica).findByText('Принято Метрикой: 1')).toBeInTheDocument()
    expect(within(savedMetrica).getByText('К выгрузке: 0')).toBeInTheDocument()
  })

  it('does not expose integrations to roles without permission', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'CASHIER')
    expect(await menuHas(user, 'Интеграции')).toBe(false)
  })
})
