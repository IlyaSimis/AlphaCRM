import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderApp } from '../test/helpers'

describe('тема оформления', () => {
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
