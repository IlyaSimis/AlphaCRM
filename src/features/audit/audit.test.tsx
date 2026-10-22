import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { chooseRole, jsonResponse, renderApp } from '../../test/helpers'

describe('журнал аудита', () => {
  it('загружает журнал аудита с сервера', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      const address = String(url)
      if (address.includes('/audit')) return jsonResponse([{ id: 'e1', occurredAt: '2026-10-20T09:00:00Z', actorId: '00000000-0000-0000-0000-000000000901', action: 'PATIENT_UPDATED', objectType: 'PATIENT', objectId: '00000000-0000-0000-0000-000000000103', result: 'ALLOWED', reason: 'phone', correlationId: null }])
      if (address.includes('/patients')) return jsonResponse([])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }))
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await chooseRole(user, 'Менеджер')
    await user.click(screen.getByRole('button', { name: 'Журнал аудита' }))
    expect(await screen.findByText('Изменение данных пациента')).toBeInTheDocument()
    expect(screen.getByText('Елена Смирнова')).toBeInTheDocument()
    expect(screen.getByText('изменены: телефон')).toBeInTheDocument()
    expect(screen.queryByText('phone')).not.toBeInTheDocument()
  })
})
