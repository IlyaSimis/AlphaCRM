import { describe, expect, it } from 'vitest'
import { reasonTextProblem, untilText } from './breakGlass'

describe('break-glass', () => {
  it('the reason needs an explanation of at least ten characters', () => {
    expect(reasonTextProblem('  коротко ')).not.toBeNull()
    expect(reasonTextProblem('Жалоба на лечение')).toBeNull()
  })

  it('shows when the access ends in clinic time', () => {
    expect(untilText('2026-10-28T11:32:00Z', 'Europe/Moscow')).toBe('до 14:32')
    expect(untilText(null)).toBe('')
  })
})
