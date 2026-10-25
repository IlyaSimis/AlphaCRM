import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, enterDemo, goOffline, jsonResponse, openSchedule, renderApp, withAuth } from '../../test/helpers'

describe('week in the schedule', () => {
  it('shows seven days with the same appointments and actions; a day off takes no booking', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openSchedule(user)
    await user.click(screen.getByRole('button', { name: 'Неделя' }))
    expect(screen.getByText('Неделя 19.10 – 25.10')).toBeInTheDocument()

    const tuesday = screen.getByRole('group', { name: 'Александр Петров, Вт 20.10' })
    expect(within(tuesday).getByText('Анна Михайлова')).toBeInTheDocument()
    expect(within(tuesday).getByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' })).toBeInTheDocument()

    const wednesday = screen.getByRole('group', { name: 'Александр Петров, Ср 21.10' })
    expect(within(wednesday).getByText('Выходной')).toBeInTheDocument()
    expect(within(wednesday).getByRole('button', { name: 'Не принимает' })).toBeDisabled()

    await user.click(within(tuesday).getByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' }))
    expect(await within(tuesday).findByText('Отменена')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Следующая неделя' }))
    expect(screen.getByText('Неделя 26.10 – 01.11')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'День' }))
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-27')
  })

  it('no booking into free time on a day off', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openSchedule(user)
    expect(screen.getAllByText('09:00–18:00').length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'Следующий день' }))
    expect(screen.getByText('На этот день записей нет')).toBeInTheDocument()
    expect(screen.getByText('Выходной: врачи не принимают')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Выходной — запись недоступна' })).toBeDisabled()
  })

  it('on the server loads a week with one from–to request', async () => {
    const fetchMock = withAuth((address) => {
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([{ id: '00000000-0000-0000-0000-000000000390', patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-22T09:00:00Z', endsAt: '2026-10-22T09:30:00Z', serviceName: 'Повторный приём', status: 'BOOKED' }])
      return jsonResponse({}, 404)
    }, 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Неделя' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/appointments?from=2026-10-19&to=2026-10-25'))).toBe(true))
    const thursday = screen.getByRole('group', { name: 'Александр Петров, Чт 22.10' })
    expect(await within(thursday).findByText('Анна Михайлова')).toBeInTheDocument()
  })
})
