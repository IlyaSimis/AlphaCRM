import { describe, expect, it } from 'vitest'
import { PETROV_ID } from '../../api/client'
import { demoPractitioners, demoServices } from '../../api/schedule'
import type { PatientItem } from '../../api/types'
import { formatMoney, localBooking, validateBooking, type BookingDraft } from './booking'

const anna: PatientItem = { id: 'p1', name: 'Анна Михайлова', birth: '14.05.1991', birthDate: '1991-05-14', phone: '+7', consent: 'Действует', consents: [] }
const [first, repeat] = demoServices
const draft: BookingDraft = { patientId: 'p1', date: '2026-10-22', time: '14:20', serviceId: first.id, practitionerId: PETROV_ID }
const check = (change: Partial<BookingDraft>) => validateBooking({ ...draft, ...change }, [anna], demoServices, demoPractitioners)

describe('черновик записи', () => {
  it('требует пациента, дату, время, услугу и врача', () => {
    expect(check({})).toBeNull()
    expect(check({ patientId: 'nope' })).toBe('Выберите пациента из списка')
    expect(check({ date: '' })).toBe('Укажите дату приёма')
    expect(check({ time: '' })).toBe('Укажите время приёма')
    expect(check({ serviceId: 'nope' })).toBe('Выберите услугу')
  })

  it('показывает ту же ошибку рабочих часов, что и сервер', () => {
    expect(check({ date: '2026-10-21' })).toBe('Время вне рабочих часов врача')
    expect(check({ time: '17:30' })).toBe('Время вне рабочих часов врача')
    expect(check({ time: '17:30', serviceId: repeat.id })).toBeNull()
  })

  it('берёт длительность и цену из справочника услуг', () => {
    expect(localBooking(draft, anna, first)).toMatchObject({ duration: '50 мин', minutes: 50, type: 'Первичный приём', statusCode: 'BOOKED', amountMinor: 450000, patientId: 'p1' })
    expect(localBooking({ ...draft, serviceId: repeat.id }, anna, repeat).duration).toBe('30 мин')
  })

  it('форматирует сумму в рублях', () => {
    expect(formatMoney(450000).replace(/\s/g, ' ')).toBe('4 500,00 ₽')
  })
})
