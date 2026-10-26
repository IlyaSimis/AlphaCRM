import { afterEach, describe, expect, it } from 'vitest'
import { setClinicTimeZone } from './clinicTime'
import { clinicHour, dayPeriod, greeting } from './greeting'

afterEach(() => setClinicTimeZone('UTC'))

const at = (hour: number) => new Date(Date.UTC(2026, 9, 20, hour, 30))

describe('greeting', () => {
  it('switches at 5, 12 and 18 o’clock', () => {
    expect(dayPeriod(4)).toBe('evening')
    expect(dayPeriod(5)).toBe('morning')
    expect(dayPeriod(11)).toBe('morning')
    expect(dayPeriod(12)).toBe('day')
    expect(dayPeriod(17)).toBe('day')
    expect(dayPeriod(18)).toBe('evening')
    expect(dayPeriod(0)).toBe('evening')
    expect(greeting('Анна Михайлова', at(4))).toBe('Добрый вечер, Анна')
    expect(greeting('Анна Михайлова', at(5))).toBe('Доброе утро, Анна')
    expect(greeting('Анна Михайлова', at(11))).toBe('Доброе утро, Анна')
    expect(greeting('Анна Михайлова', at(12))).toBe('Добрый день, Анна')
    expect(greeting('Анна Михайлова', at(17))).toBe('Добрый день, Анна')
    expect(greeting('Анна Михайлова', at(18))).toBe('Добрый вечер, Анна')
  })

  it('takes the first word of the trimmed name; an empty name gives the phrase alone', () => {
    expect(greeting('  Анна   Михайлова ', at(9))).toBe('Доброе утро, Анна')
    expect(greeting('Администратор', at(9))).toBe('Доброе утро, Администратор')
    expect(greeting('', at(9))).toBe('Доброе утро')
    expect(greeting('   ', at(13))).toBe('Добрый день')
    expect(greeting(null, at(20))).toBe('Добрый вечер')
  })

  it('uses the clinic clock, not the browser one', () => {
    setClinicTimeZone('Europe/Moscow')
    // 01:30 UTC — 04:30 в Москве, ещё вечер; 02:30 UTC — 05:30, уже утро.
    expect(clinicHour(at(1))).toBe(4)
    expect(greeting('Анна', at(1))).toBe('Добрый вечер, Анна')
    expect(greeting('Анна', at(2))).toBe('Доброе утро, Анна')
    // 14:30 UTC — 17:30 в Москве, ещё день; 15:30 UTC — уже вечер.
    expect(greeting('Анна', at(14))).toBe('Добрый день, Анна')
    expect(greeting('Анна', at(15))).toBe('Добрый вечер, Анна')
  })
})
