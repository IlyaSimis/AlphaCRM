import { describe, expect, it } from 'vitest'
import { passwordProblem } from './password'

describe('new password', () => {
  it('at least 8 characters and equal to the repeat', () => {
    expect(passwordProblem('short', 'short')).toBe('Пароль должен быть не короче 8 символов')
    expect(passwordProblem('long-enough', 'long-enougH')).toBe('Пароли не совпадают')
    expect(passwordProblem('long-enough', 'long-enough')).toBeNull()
  })
})
