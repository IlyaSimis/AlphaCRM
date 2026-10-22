import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'
import App from '../app/App'
import { ClinicThemeProvider } from '../app/theme'

export const renderApp = () => render(<ClinicThemeProvider><App /></ClinicThemeProvider>)

export function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body }
}

/** Сервер недоступен: интерфейс работает в демо-режиме. */
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

type User = ReturnType<typeof userEvent.setup>

export async function chooseRole(user: User, role: 'Врач' | 'Администратор' | 'Менеджер') {
  await user.click(screen.getByLabelText('Роль пользователя'))
  await user.click(screen.getByRole('option', { name: role }))
}
