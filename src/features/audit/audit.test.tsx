import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { jsonResponse, renderApp, withAuth } from '../../test/helpers'

describe('audit log', () => {
  it('loads the audit log from the server', async () => {
    withAuth(async (url: string) => {
      const address = String(url)
      if (address.includes('/audit')) return jsonResponse([{ id: 'e1', occurredAt: '2026-10-20T09:00:00Z', actorId: '00000000-0000-0000-0000-0000000a0001', actorName: 'Администратор', action: 'PATIENT_UPDATED', objectType: 'PATIENT', objectId: '00000000-0000-0000-0000-000000000103', result: 'ALLOWED', reason: 'phone', correlationId: null }])
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
})
