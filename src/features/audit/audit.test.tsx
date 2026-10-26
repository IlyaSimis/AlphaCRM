import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { jsonResponse, renderApp, withAuth } from '../../test/helpers'

describe('audit log', () => {
  it('loads the audit log from the server', async () => {
    withAuth(async (url: string) => {
      const address = String(url)
      if (address.includes('/audit')) return jsonResponse({ page: 0, size: 20, total: 1, items: [{ id: 'e1', occurredAt: '2026-10-20T09:00:00Z', actorId: '00000000-0000-0000-0000-0000000a0001', actorName: 'Администратор', action: 'PATIENT_UPDATED', objectType: 'PATIENT', objectId: '00000000-0000-0000-0000-000000000103', result: 'ALLOWED', reason: 'phone', correlationId: null }] })
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 't.manager')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Журнал аудита' }))
    expect(await screen.findByText('Изменение данных пациента')).toBeInTheDocument()
    // Имя автора подставляет сервер из сотрудников своей клиники.
    expect(screen.getByText('Администратор', { selector: 'td' })).toBeInTheDocument()
    expect(screen.getByText('изменены: телефон')).toBeInTheDocument()
    expect(screen.queryByText('phone')).not.toBeInTheDocument()
  })

  it('shows 20 events per page and asks the server for the next page', async () => {
    const event = (n: number) => ({ id: `e${n}`, occurredAt: '2026-10-20T09:00:00Z', actorId: '00000000-0000-0000-0000-0000000a0001', actorName: 'Администратор', action: 'PATIENT_UPDATED', objectType: 'PATIENT', objectId: `00000000-0000-0000-0000-0000000001${String(n).padStart(2, '0')}`, result: 'ALLOWED', reason: 'phone', correlationId: null })
    const fetchMock = withAuth(async (url: string) => {
      const address = new URL(String(url), 'http://localhost')
      if (address.pathname.endsWith('/audit/page')) {
        const page = Number(address.searchParams.get('page'))
        const items = Array.from({ length: page === 0 ? 20 : 5 }, (_, i) => event(page * 20 + i))
        return jsonResponse({ page, size: 20, total: 25, items })
      }
      if (address.pathname.includes('/patients')) return jsonResponse([])
      if (address.pathname.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 't.manager')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Журнал аудита' }))
    await waitFor(() => expect(screen.getAllByText('Изменение данных пациента')).toHaveLength(20))
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/audit/page?page=0&size=20'))).toBe(true)
    await user.click(screen.getByRole('button', { name: 'Страница 2' }))
    await waitFor(() => expect(screen.getAllByText('Изменение данных пациента')).toHaveLength(5))
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/audit/page?page=1&size=20'))).toBe(true)
  })
})
