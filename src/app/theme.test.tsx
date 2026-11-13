import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { enterDemo, goOffline, renderApp } from '../test/helpers'

describe('color theme', () => {
  it('switches the theme and remembers the choice', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)

    await user.click(screen.getByRole('button', { name: 'Настройки' }))
    const darkMode = screen.getByRole('switch', { name: 'Тёмная тема' })
    expect(darkMode).not.toBeChecked()
    await user.click(darkMode)

    await waitFor(() => expect(document.documentElement).toHaveAttribute('data-theme', 'dark'))
    expect(localStorage.getItem('alphacrm-color-mode')).toBe('dark')
    expect(screen.getByRole('switch', { name: 'Тёмная тема' })).toBeChecked()
  })

  it('uses the system dark theme on first start', async () => {
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

    goOffline()
    renderApp()

    await waitFor(() => expect(document.documentElement).toHaveAttribute('data-theme', 'dark'))
  })
})
