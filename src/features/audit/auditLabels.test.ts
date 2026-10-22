import { describe, expect, it } from 'vitest'
import { reasonLabel } from './auditLabels'

describe('колонка «Основание» в журнале аудита', () => {
  it('прячет коды, которые повторяют действие', () => {
    for (const code of ['booking', 'cancel', 'treatment', 'attestation', 'correction', 'billing', 'registration']) {
      expect(reasonLabel(code)).toBe('—')
    }
    expect(reasonLabel(null)).toBe('—')
    expect(reasonLabel('')).toBe('—')
  })

  it('переводит источник просмотра и отказ в доступе', () => {
    expect(reasonLabel('search')).toBe('из поиска')
    expect(reasonLabel('card')).toBe('из карточки')
    expect(reasonLabel('no-care-relationship')).toBe('нет активной связи с лечением')
  })

  it('перечисляет изменённые поля пациента без значений', () => {
    expect(reasonLabel('no-changes')).toBe('поля не изменились')
    expect(reasonLabel('phone')).toBe('изменены: телефон')
    expect(reasonLabel('fullName,phone')).toBe('изменены: ФИО, телефон')
    expect(reasonLabel('fullName,birthDate,phone')).toBe('изменены: ФИО, дата рождения, телефон')
  })

  it('показывает переходы статуса русскими названиями, как в расписании', () => {
    expect(reasonLabel('BOOKED->CONFIRMED')).toBe('Ожидает → Подтверждён')
    expect(reasonLabel('IN_PROGRESS->COMPLETED')).toBe('На приёме → Завершён')
    expect(reasonLabel('to ARRIVED')).toBe('попытка перевести в Прибыл')
  })

  it('подписывает новые события: перенос, согласие, счёт', () => {
    expect(reasonLabel('2026-10-20T09:00Z>2026-10-22T10:30Z')).toBe('20.10.2026 09:00 → 22.10.2026 10:30')
    expect(reasonLabel('2026-10-20T09:00Z>2026-10-20T11:00Z;practitioner')).toBe('20.10.2026 09:00 → 20.10.2026 11:00, другой врач')
    expect(reasonLabel('granted')).toBe('выдано')
    expect(reasonLabel('revoked')).toBe('отозвано')
    expect(reasonLabel('appointment-completed')).toBe('после завершения приёма')
  })

  it('неизвестный код показывает как есть', () => {
    expect(reasonLabel('something-new')).toBe('something-new')
    expect(reasonLabel('phone,email')).toBe('phone,email')
    expect(reasonLabel('BOOKED->LOST')).toBe('BOOKED->LOST')
    expect(reasonLabel('to SOMEWHERE')).toBe('to SOMEWHERE')
  })
})
