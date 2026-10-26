import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import css from '../index.css?raw'
import { enterDemo, goOffline, menu, renderApp } from '../test/helpers'

const content = () => document.querySelector('.content') as HTMLElement

describe('workspace background and greeting', () => {
  it('one landscape behind the work column, hidden from screen readers; the sign-in page has none', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await screen.findByRole('button', { name: 'Песочница: Администратор' })
    expect(document.querySelectorAll('.landscape')).toHaveLength(0)
    await enterDemo(user, 'ADMINISTRATOR')
    const scenes = document.querySelectorAll('.landscape')
    expect(scenes).toHaveLength(1)
    expect(scenes[0].closest('.main')).not.toBeNull()
    expect(scenes[0].closest('.sidebar')).toBeNull()
    expect(scenes[0]).toHaveAttribute('aria-hidden', 'true')
    expect(scenes[0].querySelectorAll('svg')).toHaveLength(1)
    // «Сейчас» в тестах — 06:00 по часам клиники: утренняя сцена с солнцем.
    expect(scenes[0]).toHaveClass('landscape--morning')
    expect(scenes[0].querySelector('.ls-sun')).not.toBeNull()

    // Смена вкладки не создаёт вторую сцену и не проигрывает выезд заново.
    await user.click(menu().getByRole('button', { name: 'Пациенты' }))
    expect(document.querySelectorAll('.landscape')).toHaveLength(1)
    expect(document.querySelector('.landscape')).toBe(scenes[0])
  })

  it('the slide-in runs once and is switched off for reduced motion', () => {
    expect(css).toMatch(/animation-fill-mode:\s*forwards/)
    expect(css).toMatch(/animation-iteration-count:\s*1/)
    expect(css).not.toMatch(/landscape[^}]*infinite/)
    const reduced = /@media \(prefers-reduced-motion: reduce\)\s*\{([^{}]*\{[^}]*\})/.exec(css)
    expect(reduced?.[1]).toMatch(/\.landscape/)
    expect(reduced?.[1]).toMatch(/animation:\s*none/)
    expect(reduced?.[1]).toMatch(/opacity:\s*1/)
  })

  it('the doctor is greeted above the schedule on any day; the administrator is not', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'DOCTOR')
    expect(within(content()).getByText('Доброе утро, Врач')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Следующий день' }))
    expect(within(content()).getByText('Доброе утро, Врач')).toBeInTheDocument()
    // В меню — по-прежнему полное ФИО.
    expect(menu().getByText('Врач песочницы')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'ADMINISTRATOR')
    expect(within(content()).getByText('Доброе утро, Администратор')).toBeInTheDocument()
    await user.click(menu().getByRole('button', { name: 'Расписание' }))
    expect(within(content()).queryByText(/^(Доброе утро|Добрый день|Добрый вечер)/)).not.toBeInTheDocument()
  })
})
