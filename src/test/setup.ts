import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach, vi } from 'vitest'
import { resetSandbox } from '../shared/sandbox'

beforeEach(() => {
  // «Сегодня» в тестах — день демо-данных: расписание, «не в прошлом» и сроки задач не зависят
  // от дня запуска. Подменяется только Date, таймеры настоящие (нужны userEvent).
  vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-10-20T06:00:00Z') })
  localStorage.clear()
  resetSandbox()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
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

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
