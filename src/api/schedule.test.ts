import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './client'
import { createAppointment, statusLabel, toScheduleItem, toneFor, withStatus } from './schedule'

afterEach(() => vi.unstubAllGlobals())

describe('API расписания', () => {
  it('превращает ответ сервера в строку расписания', () => {
    const item = toScheduleItem({
      id: 'a1',
      patientId: '00000000-0000-0000-0000-000000000102',
      practitionerId: '00000000-0000-0000-0000-000000000202',
      startsAt: '2026-10-20T10:10:00Z',
      endsAt: '2026-10-20T10:40:00Z',
      serviceName: 'Повторный приём',
      status: 'NO_SHOW',
    }, '2026-10-20')
    expect(item).toMatchObject({ time: '10:10', duration: '30 мин', name: 'Дмитрий Волков', status: 'Неявка', tone: 'red' })
  })

  it('подписывает все статусы и меняет статус строки целиком', () => {
    expect(statusLabel('COMPLETED')).toBe('Завершён')
    expect(statusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(toneFor('CANCELLED')).toBe('muted')
    const item = toScheduleItem({ id: 'a', patientId: 'x', startsAt: '2026-10-20T09:00:00Z', endsAt: '2026-10-20T09:30:00Z', serviceName: 'Приём', status: 'BOOKED' }, '2026-10-20')
    expect(withStatus(item, 'ARRIVED')).toMatchObject({ statusCode: 'ARRIVED', status: 'Прибыл', tone: 'green' })
  })

  it('отдаёт сообщение сервера при конфликте записи', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ ok: false, status: 409, json: async () => ({ code: 'SLOT_ALREADY_BOOKED', message: 'Интервал врача уже занят' }) })))
    const attempt = createAppointment({ patientId: 'p', patientName: 'Анна', practitionerId: 'd', date: '2026-10-20', time: '10:00', minutes: 30, serviceName: 'Приём' })
    await expect(attempt).rejects.toMatchObject({ status: 409, code: 'SLOT_ALREADY_BOOKED' })
    await expect(attempt).rejects.toBeInstanceOf(ApiError)
  })
})
