import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { ClinicThemeProvider } from './theme'

const renderApp = () => render(<ClinicThemeProvider><App /></ClinicThemeProvider>)

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

beforeEach(() => {
  localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
  window.matchMedia = vi.fn().mockImplementation(() => ({
    matches: false,
    media: '',
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
})

describe('основные сценарии', () => {
  it('открывает список пациентов и карточку выбранного человека', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    expect(screen.getByRole('heading', { name: 'Поиск пациента' })).toBeInTheDocument()
    await user.click(screen.getByText('Дмитрий Волков'))
    expect(screen.getByRole('heading', { name: 'Дмитрий Волков' })).toBeInTheDocument()
    expect(screen.getByText('+7 903 782-44-16')).toBeInTheDocument()
  })

  it('создаёт запись из расписания', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Новая запись')
    await waitFor(() => expect(screen.getByLabelText('Время')).toHaveValue('13:00'))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '15:45' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('15:45')).toBeInTheDocument()
    expect(screen.getByText('Демо-режим: запись видна только до обновления страницы')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('оставляет форму открытой, если выбранное время уже занято', async () => {
    const anna = {
      id: '00000000-0000-0000-0000-000000000101',
      fullName: 'Анна Михайлова',
      birthDate: '1991-05-14',
      contacts: [{ type: 'PHONE', value: '+79160000000' }],
      consents: [{ revokedAt: null }],
    }
    let appointments: Array<Record<string, string>> = []
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (String(url).includes('/patients')) return jsonResponse([anna])
      if (String(url).includes('/appointments') && method === 'POST') {
        const body = JSON.parse(String(init?.body))
        if (appointments.some((item) => item.startsAt === body.startsAt)) {
          return jsonResponse({ code: 'SLOT_ALREADY_BOOKED', message: 'Интервал врача уже занят' }, 409)
        }
        const created = { id: 'appt-1', ...body, status: 'BOOKED', version: 0 }
        appointments = [...appointments, created]
        return jsonResponse(created, 201)
      }
      if (String(url).includes('/appointments')) return jsonResponse(appointments)
      return jsonResponse({}, 404)
    }))
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    await waitFor(() => expect(screen.getByLabelText('Время')).toHaveValue('13:00'))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '16:10' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('16:10')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    await waitFor(() => expect(screen.getByLabelText('Время')).toHaveValue('13:00'))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '16:10' } })
    expect(screen.getByLabelText('Время')).toHaveValue('16:10')
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('Интервал врача уже занят')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getAllByText('16:10')).toHaveLength(1)
  })

  it('добавляет пациента в список', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByRole('button', { name: /новый пациент/i }))
    await user.clear(screen.getByLabelText('ФИО'))
    await user.type(screen.getByLabelText('ФИО'), 'Ирина Белова')
    await user.click(screen.getByRole('button', { name: /сохранить пациента/i }))
    expect(await screen.findByText('Ирина Белова')).toBeInTheDocument()
  })

  it('скрывает клинические данные от администратора', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByLabelText('Роль пользователя'))
    await user.click(screen.getByRole('option', { name: 'Администратор' }))
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText('Клинические данные недоступны')).toBeInTheDocument()
    expect(screen.getByText(/Роль «Администратор» не имеет доступа/)).toBeInTheDocument()
    expect(screen.queryByText(/Тревожно-депрессивная/)).not.toBeInTheDocument()
  })

  it('переключает день расписания и показывает запись на выбранную дату', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const user = userEvent.setup()
    renderApp()
    expect(screen.getByText('Анна Михайлова')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Следующий день' }))
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-21')
    expect(screen.queryByText('Анна Михайлова')).not.toBeInTheDocument()
    expect(screen.getByText('На этот день записей нет')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    fireEvent.change(screen.getByLabelText('Дата'), { target: { value: '2026-10-22' } })
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '14:20' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('14:20')).toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-22')
    expect(screen.queryByText('Дмитрий Волков')).not.toBeInTheDocument()
  })

  it('даёт выбрать день в карточке пациента отдельно от расписания', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Дмитрий Волков'))
    expect(screen.getByText(/10:10/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Следующий день пациента' }))
    expect(screen.getByLabelText('День пациента')).toHaveValue('2026-10-21')
    expect(screen.getByText('На этот день записей нет')).toBeInTheDocument()
    expect(screen.queryByText(/10:10/)).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('День пациента'), { target: { value: '2026-10-20' } })
    expect(screen.getByText(/10:10/)).toBeInTheDocument()
  })

  it('отменяет запись только у администратора', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    const user = userEvent.setup()
    renderApp()
    expect(screen.queryByRole('button', { name: /отменить запись/i })).not.toBeInTheDocument()
    await user.click(screen.getByLabelText('Роль пользователя'))
    await user.click(screen.getByRole('option', { name: 'Администратор' }))
    await user.click(screen.getByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' }))
    expect(await screen.findByText('Демо-режим: отмена видна только до обновления страницы')).toBeInTheDocument()
    expect(screen.getByText('Отменена')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Отменить запись 09:00 Анна Михайлова' })).toBeInTheDocument()
  })

  it('подписывает и блокирует врачебный документ', async () => {
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: /начать приём/i }))
    await user.click(screen.getByRole('button', { name: /проверить и подписать/i }))
    expect(screen.getByText(/после подписания эту версию нельзя изменить/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /подписать и заблокировать/i }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText(/документ подписан 20\.10\.2026/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /создать дополнение/i })).toBeInTheDocument()
  })

  it('переключает тему и сохраняет выбор', async () => {
    const user = userEvent.setup()
    renderApp()

    await user.click(screen.getByRole('button', { name: 'Включить тёмную тему' }))

    await waitFor(() => expect(document.documentElement).toHaveAttribute('data-theme', 'dark'))
    expect(localStorage.getItem('quiet-clinic-color-mode')).toBe('dark')
    expect(screen.getByRole('button', { name: 'Включить светлую тему' })).toBeInTheDocument()
  })

  it('использует системную тёмную тему при первом запуске', async () => {
    window.matchMedia = vi.fn().mockImplementation(() => ({
      matches: true,
      media: '(prefers-color-scheme: dark)',
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))

    renderApp()

    await waitFor(() => expect(document.documentElement).toHaveAttribute('data-theme', 'dark'))
  })
})
