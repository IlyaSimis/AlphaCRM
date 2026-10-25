import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PETROV_ID } from '../../api/client'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth } from '../../test/helpers'

const appointment = (id: string, startsAt: string, status: string, extra: Record<string, unknown> = {}) => ({
  id, patientId: anna.id, practitionerId: PETROV_ID, startsAt, endsAt: startsAt.replace(':00:00Z', ':30:00Z'), serviceName: 'Повторный приём', status, ...extra,
})

/** Сервер «Сегодня»: записи на сегодня и завтра, задачи до сегодня, завершённые без счёта, ошибка доставки. */
const server = (tasks: unknown[] = [
  { id: 't-1', patientId: anna.id, title: 'Перезвонить в лабораторию', dueDate: '2026-10-19', assigneeId: '00000000-0000-0000-0000-0000000a0001', status: 'OPEN' },
  { id: 't-2', patientId: null, title: 'Задача на сегодня', dueDate: '2026-10-20', assigneeId: '00000000-0000-0000-0000-0000000a0001', status: 'OPEN' },
]) => (address: string) => {
  const url = new URL(address, 'http://localhost')
  if (address.includes('/patients')) return jsonResponse([anna])
  if (address.includes('/registry/tasks')) return jsonResponse(tasks)
  if (address.includes('/registry/waitlist')) return jsonResponse([])
  if (address.includes('/charges')) return jsonResponse([])
  if (url.pathname.endsWith('/outbox')) return jsonResponse([{
    id: 'o-1', topic: 'NOTIFICATION', template: 'appointment-reminder', aggregateType: 'APPOINTMENT', aggregateId: 'a-1', appointmentStartsAt: '2026-10-21T09:00:00Z',
    channel: 'WEBHOOK', status: 'FAILED', reason: 'HTTP_500', attempts: 5, createdAt: '2026-10-19T08:00:00Z', lastAttemptAt: '2026-10-19T09:15:00Z', nextAttemptAt: null, sentAt: null,
  }])
  if (address.includes('/appointments')) {
    if (url.searchParams.get('status') === 'COMPLETED') return jsonResponse([appointment('a-done', '2026-10-15T09:00:00Z', 'COMPLETED', { amountMinor: 300000, currency: 'RUB' })])
    if (url.searchParams.get('from') === '2026-10-20') return jsonResponse([
      appointment('a-1', '2026-10-21T09:00:00Z', 'CONFIRMED'),
      appointment('a-2', '2026-10-20T11:00:00Z', 'CANCELLED'),
    ])
    return jsonResponse([])
  }
  return jsonResponse({}, 404)
}

describe('«Сегодня»', () => {
  it('the administrator starts here and sees all four blocks; overdue skips today; a click opens the section', async () => {
    const fetchMock = withAuth(server(), 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Сегодня', level: 4 })).toBeInTheDocument()

    const upcoming = screen.getByRole('region', { name: 'Записи на сегодня и завтра' })
    expect(await within(upcoming).findByText('завтра 09:00')).toBeInTheDocument()
    expect(within(upcoming).getByText('Анна Михайлова')).toBeInTheDocument()
    expect(within(upcoming).getByText('Подтверждён')).toBeInTheDocument()
    expect(within(upcoming).queryByText('Отменена')).not.toBeInTheDocument()

    const overdue = screen.getByRole('region', { name: 'Просроченные задачи' })
    expect(await within(overdue).findByText(/Перезвонить в лабораторию/)).toBeInTheDocument()
    expect(within(overdue).queryByText(/Задача на сегодня/)).not.toBeInTheDocument()

    const unpaid = screen.getByRole('region', { name: 'Завершены и не оплачены' })
    expect(await within(unpaid).findByText(/3\s000,00/)).toBeInTheDocument()
    expect(within(unpaid).getByText('15.10 · Счёт не выставлен')).toBeInTheDocument()

    const failed = screen.getByRole('region', { name: 'Ошибки доставки' })
    expect(await within(failed).findByText('Напоминание за сутки · Вебхук клиники')).toBeInTheDocument()
    expect(within(failed).getByText('получатель ответил 500')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/outbox?status=FAILED'))).toBe(true)

    await user.click(within(overdue).getByRole('button', { name: 'Просроченная задача: Перезвонить в лабораторию' }))
    expect(await screen.findByText('ЗАДАЧИ НА СЕГОДНЯ')).toBeInTheDocument()
    await user.click(within(document.querySelector('.desktop-sidebar') as HTMLElement).getByRole('button', { name: 'Сегодня' }))
    await user.click(await screen.findByRole('button', { name: /^Открыть карточку: Анна Михайлова/ }))
    expect(await screen.findByRole('heading', { name: 'Анна Михайлова' })).toBeInTheDocument()
  })

  it('a cashier sees no delivery errors and does not ask for them; an empty block says so', async () => {
    const fetchMock = withAuth(server([]), 't.cashier')
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Завершены и не оплачены' })).toBeInTheDocument()
    expect(await screen.findByText('Просроченных задач нет')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Ошибки доставки' })).not.toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/charges'))).toBe(true))
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/outbox'))).toBe(false)
  })

  it('a manager sees appointments and tasks, but neither money nor deliveries; a doctor has no such page', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'MANAGER')
    expect(screen.getByRole('region', { name: 'Записи на сегодня и завтра' })).toBeInTheDocument()
    // В песочнице задача «Перезвонить» просрочена со вчера, «Обновить согласие» — на сегодня.
    const overdue = screen.getByRole('region', { name: 'Просроченные задачи' })
    expect(within(overdue).getByText(/Перезвонить/)).toBeInTheDocument()
    expect(within(overdue).queryByText(/Обновить согласие/)).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Завершены и не оплачены' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Ошибки доставки' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'DOCTOR')
    const menu = document.querySelector('.desktop-sidebar') as HTMLElement
    expect(within(menu).queryByRole('button', { name: 'Сегодня' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Просроченные задачи' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toBeInTheDocument()
  })
})
