import { describe, expect, it } from 'vitest'
import { LEBEDEVA_ID, PETROV_ID } from '../api/client'
import { canSeeAnalytics, canSeeCashier, parseDoctor, parseRole, profileFor } from './roles'

describe('роли интерфейса', () => {
  it('разбирает сохранённую роль и врача', () => {
    expect(parseRole('Кассир')).toBe('Кассир')
    expect(parseRole('Регистратор')).toBe('Администратор')
    expect(parseRole(null)).toBe('Врач')
    expect(parseDoctor(LEBEDEVA_ID)).toBe(LEBEDEVA_ID)
    expect(parseDoctor('кто-то')).toBe(PETROV_ID)
  })

  it('показывает имя выбранного врача и сотрудников', () => {
    expect(profileFor('Врач', LEBEDEVA_ID).name).toBe('Ольга Лебедева')
    expect(profileFor('Кассир', PETROV_ID).name).toBe('Наталья Орлова')
  })

  it('открывает кассу кассиру и администратору, аналитику — администратору и менеджеру', () => {
    expect(canSeeCashier('Кассир')).toBe(true)
    expect(canSeeCashier('Администратор')).toBe(true)
    expect(canSeeCashier('Врач')).toBe(false)
    expect(canSeeCashier('Менеджер')).toBe(false)
    expect(canSeeAnalytics('Кассир')).toBe(false)
    expect(canSeeAnalytics('Менеджер')).toBe(true)
  })
})
