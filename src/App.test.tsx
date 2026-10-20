import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { ClinicThemeProvider } from './theme'

const renderApp = () => render(<ClinicThemeProvider><App /></ClinicThemeProvider>)

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
  it('создаёт запись из расписания', async () => {
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Новая запись')
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('добавляет пациента в список', async () => {
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByRole('button', { name: /новый пациент/i }))
    await user.clear(screen.getByLabelText('ФИО'))
    await user.type(screen.getByLabelText('ФИО'), 'Ирина Белова')
    await user.click(screen.getByRole('button', { name: /сохранить пациента/i }))
    expect(await screen.findByText('Ирина Белова')).toBeInTheDocument()
  })

  it('скрывает клинические данные от регистратора', async () => {
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByLabelText('Роль пользователя'))
    await user.click(screen.getByRole('option', { name: 'Регистратор' }))
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText('Клинические данные недоступны')).toBeInTheDocument()
    expect(screen.queryByText(/Тревожно-депрессивная/)).not.toBeInTheDocument()
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
