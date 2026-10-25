import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { OutboxItem } from '../../api/types'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth } from '../../test/helpers'
import { reasonText } from './outboxLabels'

const row = (patch: Partial<OutboxItem>): OutboxItem => ({
  id: '00000000-0000-0000-0000-0000000b0001', topic: 'NOTIFICATION', template: 'appointment-confirmed', aggregateType: 'APPOINTMENT',
  aggregateId: '00000000-0000-0000-0000-000000000301', appointmentStartsAt: '2026-11-03T09:00:00Z', channel: 'TELEGRAM', status: 'SENT',
  reason: null, attempts: 0, createdAt: '2026-10-20T08:00:00Z', lastAttemptAt: '2026-10-20T08:00:30Z', nextAttemptAt: null, sentAt: '2026-10-20T08:00:31Z', ...patch,
})

const api = (rows: OutboxItem[]) => (address: string) => {
  if (address.endsWith('/outbox')) return jsonResponse(rows)
  if (address.includes('/patients')) return jsonResponse([anna])
  if (address.includes('/appointments')) return jsonResponse([])
  return jsonResponse({}, 404)
}

describe('outbound deliveries', () => {
  it('the administrator sees template, appointment, channel and status, without text or contact', async () => {
    withAuth(api([
      row({}),
      row({ id: '00000000-0000-0000-0000-0000000b0002', template: 'appointment-reminder', channel: 'MOCK', status: 'PENDING', reason: 'CHANNEL_NOT_CONFIGURED', sentAt: null, nextAttemptAt: '2026-11-02T09:00:00Z' }),
      row({ id: '00000000-0000-0000-0000-0000000b0003', topic: 'FISCALIZATION', template: 'receipt', aggregateType: 'PAYMENT', appointmentStartsAt: null, channel: 'CASHBOX', status: 'PENDING', reason: 'FISCAL_NOT_CONFIGURED', sentAt: null }),
    ]), 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Внешние выводы' }))
    expect(await screen.findByText('Подтверждение записи')).toBeInTheDocument()
    expect(screen.getByText('03.11.2026 09:00 · 0301')).toBeInTheDocument()
    expect(screen.getByText('Telegram')).toBeInTheDocument()
    expect(screen.getByText('Доставлено')).toBeInTheDocument()
    expect(screen.getByText('канал не настроен')).toBeInTheDocument()
    expect(screen.getByText('касса не подключена')).toBeInTheDocument()
    expect(screen.getByText('Платёж · 0003')).toBeInTheDocument()
    expect(screen.queryByText(/\+7/)).not.toBeInTheDocument()
  })

  it('doctor and cashier do not see the page', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'DOCTOR')
    expect(screen.queryByRole('button', { name: 'Внешние выводы' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'CASHIER')
    expect(screen.queryByRole('button', { name: 'Внешние выводы' })).not.toBeInTheDocument()
  })

  it('labels HTTP and unknown reasons', () => {
    expect(reasonText('HTTP_503')).toBe('получатель ответил 503')
    expect(reasonText('SOMETHING_NEW')).toBe('SOMETHING_NEW')
    expect(reasonText(null)).toBe('')
  })
})
