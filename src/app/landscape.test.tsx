import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import css from '../index.css?raw'
import { enterDemo, goOffline, menu, renderApp } from '../test/helpers'
import { FAR_HILL, GROUND_HEIGHT, Landscape, MOUNTAINS } from './Landscape'

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
    // Один inline SVG (внутри — вложенные svg для растяжения земли и позиций фигур).
    expect(scenes[0].querySelectorAll('svg.ls-scene')).toHaveLength(1)
    expect(Array.from(scenes[0].querySelectorAll('svg')).filter((svg) => !svg.parentElement?.closest('svg'))).toHaveLength(1)
    // Оба светила в сцене: какое видно, решает тема (CSS), смена темы сцену не пересоздаёт.
    expect(scenes[0].querySelectorAll('.ls-sun')).toHaveLength(1)
    expect(scenes[0].querySelectorAll('.ls-moon')).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: 'Включить тёмную тему' }))
    await waitFor(() => expect(document.documentElement).toHaveAttribute('data-theme', 'dark'))
    expect(document.querySelector('.landscape')).toBe(scenes[0])

    // Смена вкладки не создаёт вторую сцену и не проигрывает выезд заново.
    await user.click(menu().getByRole('button', { name: 'Пациенты' }))
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
    await user.click(menu().getByRole('button', { name: 'Расписание' }))
    expect(within(content()).queryByText(/^(Доброе утро|Добрый день|Добрый вечер)/)).not.toBeInTheDocument()
  })
})

/** Точки контура «x y» из d пути (команды M, L, C, S — берутся конечные и опорные точки). */
function points(d: string) {
  const numbers = d.match(/-?\d+(\.\d+)?/g)!.map(Number)
  const result: Array<{ x: number; y: number }> = []
  for (let i = 0; i + 1 < numbers.length; i += 2) result.push({ x: numbers[i], y: numbers[i + 1] })
  return result
}

describe('landscape layout', () => {
  it('top and middle are plain sky; land, peaks, crowns and the lights stay in the bottom band', () => {
    // Полоса земли — 180 px у нижнего края окна; над ней только небо цвета фона страницы.
    expect(GROUND_HEIGHT).toBe(180)
    expect(css).toMatch(/\.landscape \.ls-scene \{[^}]*bottom: 0;[^}]*height: 180px;/)
    expect(css).toMatch(/\.landscape \{ --ls-sky-top: var\(--bg\);/)
    expect(css).toMatch(/:root\[data-theme="dark"\] \.landscape \{ --ls-sky-top: var\(--bg\);/)

    const mountains = points(MOUNTAINS)
    const hill = points(FAR_HILL)
    // Верх viewBox полосы — небо: ни холм, ни вершины не поднимаются выше 30 из 180.
    for (const point of [...mountains, ...hill]) {
      expect(point.y).toBeGreaterThanOrEqual(30)
      expect(point.y).toBeLessThanOrEqual(GROUND_HEIGHT)
    }
    // Ближняя полоса — изломы (только прямые отрезки), с несколькими пиками и седловиной.
    expect(MOUNTAINS).not.toMatch(/[CSQT]/i)
    const ridge = mountains.filter((point) => point.y < GROUND_HEIGHT)
    const peaks = ridge.filter((point, i) => i > 0 && i < ridge.length - 1 && point.y < ridge[i - 1].y && point.y < ridge[i + 1].y)
    expect(peaks.length).toBeGreaterThanOrEqual(2)
    expect(new Set(peaks.map((peak) => peak.y)).size).toBe(peaks.length)
    // Пики выше дальнего холма.
    expect(Math.min(...peaks.map((peak) => peak.y))).toBeLessThan(Math.min(...hill.map((point) => point.y)))

    const { container } = render(<Landscape />)
    const scene = container.querySelector('svg.ls-scene') as SVGSVGElement
    expect(scene).toHaveAttribute('height', String(GROUND_HEIGHT))
    for (const circle of Array.from(scene.querySelectorAll('circle'))) {
      const cy = Number(circle.getAttribute('cy'))
      expect(cy).toBeGreaterThan(0)
      expect(cy).toBeLessThan(GROUND_HEIGHT)
    }
    expect(scene.querySelectorAll('.ls-crown')).toHaveLength(3)
    expect(scene.querySelector('.ls-sun')).not.toBeNull()
    expect(scene.querySelector('.ls-moon')).not.toBeNull()

    // Роща — между двумя пиками (в седловине), а не на вершине.
    const peakShares = peaks.map((peak) => (peak.x / 1440) * 100)
    const grove = Array.from(scene.querySelectorAll('.ls-trees svg')).map((tree) => parseFloat(tree.getAttribute('x') ?? ''))
    expect(grove).toHaveLength(2)
    for (const x of grove) {
      expect(peakShares.some((share) => Math.abs(share - x) < 2)).toBe(false)
      expect(peakShares.some((share) => share < x) && peakShares.some((share) => share > x)).toBe(true)
    }
    // Дальнее дерево — левее рощи.
    const far = parseFloat(scene.querySelector('.ls-tree-far svg')?.getAttribute('x') ?? '')
    expect(far).toBeLessThan(Math.min(...grove))
  })
})
