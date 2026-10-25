import { describe, expect, it } from 'vitest'
import { actionLabels, objectLabels, reasonLabel } from './auditLabels'

describe('audit log reason column', () => {
  it('hides codes that repeat the action', () => {
    for (const code of ['booking', 'cancel', 'treatment', 'attestation', 'correction', 'billing', 'registration']) {
      expect(reasonLabel(code)).toBe('—')
    }
    expect(reasonLabel(null)).toBe('—')
    expect(reasonLabel('')).toBe('—')
  })

  it('labels the view source and access refusal', () => {
    expect(reasonLabel('search')).toBe('из поиска')
    expect(reasonLabel('card')).toBe('из карточки')
    expect(reasonLabel('no-care-relationship')).toBe('нет активной связи с лечением')
  })

  it('lists changed patient fields without values', () => {
    expect(reasonLabel('no-changes')).toBe('поля не изменились')
    expect(reasonLabel('phone')).toBe('изменены: телефон')
    expect(reasonLabel('fullName,phone')).toBe('изменены: ФИО, телефон')
    expect(reasonLabel('fullName,birthDate,phone')).toBe('изменены: ФИО, дата рождения, телефон')
    expect(reasonLabel('email,secondPhone,assignedStaff')).toBe('изменены: e-mail, второй телефон, постоянный администратор')
    expect(reasonLabel('fields:fullName,specialty,room,shifts')).toBe('поля: ФИО, специальность, кабинет, график')
    expect(reasonLabel('fields:specialty,room')).toBe('поля: специальность, кабинет')
  })

  it('shows status transitions with the same labels as the schedule', () => {
    expect(reasonLabel('BOOKED->CONFIRMED')).toBe('Ожидает → Подтверждён')
    expect(reasonLabel('IN_PROGRESS->COMPLETED')).toBe('На приёме → Завершён')
    expect(reasonLabel('to ARRIVED')).toBe('попытка перевести в Прибыл')
  })

  it('labels new events: reschedule, consent, charge', () => {
    expect(reasonLabel('2026-10-20T09:00Z>2026-10-22T10:30Z')).toBe('20.10.2026 09:00 → 22.10.2026 10:30')
    expect(reasonLabel('2026-10-20T09:00Z>2026-10-20T11:00Z;practitioner')).toBe('20.10.2026 09:00 → 20.10.2026 11:00, другой врач')
    expect(reasonLabel('granted')).toBe('выдано')
    expect(reasonLabel('revoked')).toBe('отозвано')
    expect(reasonLabel('appointment-completed')).toBe('после завершения приёма')
  })

  it('labels registry and staff events', () => {
    expect(actionLabels.REGISTRY_TASK_CREATED).toBe('Задача регистратуры')
    expect(objectLabels.WAITLIST).toBe('Лист ожидания')
    expect(reasonLabel('call-back')).toBe('перезвонить')
    expect(reasonLabel('update-consent')).toBe('обновить согласие')
    expect(reasonLabel('self')).toBe('сам сотрудник')
    expect(reasonLabel('role:CASHIER')).toBe('роль: кассир')
    expect(reasonLabel('days:1,4')).toBe('рабочие дни: пн, чт')
    expect(reasonLabel('days:')).toBe('рабочих дней нет')
    expect(reasonLabel('referralSource,registryNote')).toBe('изменены: источник, заметка регистратуры')
    expect(reasonLabel('role:ROOT')).toBe('role:ROOT')
    expect(actionLabels.SIGNED_DOCUMENT_READ).toBe('Просмотр подписанного документа')
    expect(reasonLabel('list')).toBe('из списка')
  })

  it('labels a patient list read as one event', () => {
    expect(actionLabels.PATIENT_LIST_READ).toBe('Просмотр списка пациентов')
    expect(reasonLabel('list:12')).toBe('весь список, 12')
    expect(reasonLabel('search:3')).toBe('поиск, найдено 3')
  })

  it('labels outbox deliveries without text or address', () => {
    expect(actionLabels.NOTIFICATION_DELIVERED).toBe('Сообщение пациенту доставлено')
    expect(reasonLabel('appointment-reminder:telegram')).toBe('Напоминание за сутки · Telegram')
    expect(reasonLabel('receipt:cashbox')).toBe('Чек · Онлайн-касса')
    expect(reasonLabel('telegram')).toBe('изменены: Telegram')
  })

  it('shows an unknown code as is', () => {
    expect(reasonLabel('something-new')).toBe('something-new')
    expect(reasonLabel('phone,email')).toBe('phone,email')
    expect(reasonLabel('BOOKED->LOST')).toBe('BOOKED->LOST')
    expect(reasonLabel('to SOMEWHERE')).toBe('to SOMEWHERE')
  })
})
