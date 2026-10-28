import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import css from '../index.css?raw'
import { enterDemo, goOffline, menu, renderApp, openPage } from '../test/helpers'
import { Landscape, MOUNTAINS } from './Landscape'

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
    // Оба светила в сцене: какое видно, решает тема (CSS), смена темы сцену не пересоздаёт.
    expect(scenes[0].querySelectorAll('.ls-sun')).toHaveLength(1)
    expect(scenes[0].querySelectorAll('.ls-moon')).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Включить тёмную тему' }))
    await waitFor(() => expect(document.documentElement).toHaveAttribute('data-theme', 'dark'))
    expect(document.querySelector('.landscape')).toBe(scenes[0])

    // Смена вкладки не создаёт вторую сцену и не проигрывает выезд заново.
    await openPage(user, 'Пациенты')
    expect(document.querySelectorAll('.landscape')).toHaveLength(1)
    expect(document.querySelector('.landscape')).toBe(scenes[0])
  })

  it('light theme shows the sun, dark theme the moon; switching moves them, not swaps instantly', () => {
    expect(css).toMatch(/\.ls-moon \{ transform: translateY\(\d+px\); opacity: 0; \}/)
    expect(css).toMatch(/:root\[data-theme="dark"\] \.ls-moon \{ transform: translateY\(0\); opacity: 1; \}/)
    expect(css).toMatch(/:root\[data-theme="dark"\] \.ls-sun \{ transform: translateY\(\d+px\); opacity: 0; \}/)
    expect(css).toMatch(/\.ls-body \{ transition: transform/)
  })

  it('the slide-in runs once and is switched off for reduced motion', () => {
    expect(css).toMatch(/animation-fill-mode:\s*forwards/)
    expect(css).toMatch(/animation-iteration-count:\s*1/)
    expect(css).not.toMatch(/landscape[^}]*infinite/)
    const reduced = /@media \(prefers-reduced-motion: reduce\)\s*\{([^{}]*\{[^}]*\})/.exec(css)
    expect(reduced?.[1]).toMatch(/\.landscape/)
    expect(reduced?.[1]).toMatch(/animation:\s*none/)
    expect(reduced?.[1]).toMatch(/opacity:\s*1/)
    expect(css).toMatch(/prefers-reduced-motion: reduce\)[^@]*\.ls-body[^}]*transition: none/)
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
    await openPage(user, 'Расписание')
    expect(within(content()).queryByText(/^(Доброе утро|Добрый день|Добрый вечер)/)).not.toBeInTheDocument()
  })
})

describe('landscape layout', () => {
  it('the middle line is mountains; the trees stand on the front hill', () => {
    // Горы — прямые изломы с несколькими пиками разной высоты.
    expect(MOUNTAINS).not.toMatch(/[CSQT]/i)
    const numbers = MOUNTAINS.match(/\d+/g)!.map(Number)
    const ridge: Array<{ x: number; y: number }> = []
    for (let i = 0; i + 1 < numbers.length; i += 2) if (numbers[i + 1] < 900) ridge.push({ x: numbers[i], y: numbers[i + 1] })
    const peaks = ridge.filter((point, i) => i > 0 && i < ridge.length - 1 && point.y < ridge[i - 1].y && point.y < ridge[i + 1].y)
    expect(peaks.length).toBeGreaterThanOrEqual(3)
    expect(new Set(peaks.map((peak) => peak.y)).size).toBe(peaks.length)

    // Деревья нарисованы после ближнего холма — стоят на первой линии, а не за горами.
    const { container } = render(<Landscape />)
    const order = Array.from(container.querySelectorAll('.ls-far, .ls-near, .ls-trees')).map((node) => (node as Element).getAttribute('class'))
    expect(order).toEqual(['ls-far', 'ls-near', 'ls-trees'])
    expect(container.querySelectorAll('.ls-trees .ls-crown')).toHaveLength(3)
  })
})
