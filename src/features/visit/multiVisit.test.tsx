import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PETROV_ID } from '../../api/client'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth, openPage } from '../../test/helpers'

describe('several visits in a row', () => {
  it('a doctor starts visits from CONFIRMED and BOOKED without closing the previous one and completes one', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)

    await user.click(screen.getByRole('button', { name: 'Начать приём 10:10 Дмитрий Волков' }))
    expect(await screen.findByRole('heading', { name: 'Дмитрий Волков' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Диагноз'), 'Черновик первого приёма')
    await openPage(user, 'Расписание')
    // Первый приём открыт, но кнопки у соседних строк на месте.
    expect(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Начать приём 11:00 Мария Соколова' })).toBeInTheDocument()
    expect(screen.getAllByText('На приёме').length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: 'Начать приём 11:00 Мария Соколова' }))
    expect(await screen.findByRole('heading', { name: 'Мария Соколова' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Приём: Дмитрий Волков · 10:10' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Приём: Мария Соколова · 11:00' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Завершить приём' }))
    expect(await screen.findByText('Приём завершён')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Завершить приём' })).not.toBeInTheDocument()

    await openPage(user, 'Расписание')
    expect(screen.queryByRole('button', { name: 'Начать приём 11:00 Мария Соколова' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Начать приём 10:10 Дмитрий Волков' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' })).toBeInTheDocument()
    expect(screen.getByText('Завершён', { selector: '.status-chip.done *' })).toBeInTheDocument()

    // Возврат к первому приёму: черновик на месте, приём не закрыт и не подписан.
    await user.click(screen.getByRole('button', { name: 'Приём: Дмитрий Волков · 10:10' }))
    expect(await screen.findByLabelText('Диагноз')).toHaveValue('Черновик первого приёма')
    expect(screen.getByLabelText('Диагноз')).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Завершить приём' })).toBeInTheDocument()
  })

  it('on the server start sets IN_PROGRESS and completion COMPLETED; the neighbouring appointment stays', async () => {
    const first = '00000000-0000-0000-0000-000000000381'
    const second = '00000000-0000-0000-0000-000000000382'
    const statuses: Record<string, string> = { [first]: 'BOOKED', [second]: 'CONFIRMED' }
    const encounters: Record<string, string> = {}
    const row = (id: string, startsAt: string, endsAt: string) => ({ id, patientId: anna.id, practitionerId: PETROV_ID, startsAt, endsAt, serviceName: 'Повторный приём', status: statuses[id] })
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/clinical/encounters') && method === 'POST') {
        const { appointmentId } = JSON.parse(String(init?.body))
        const existing = encounters[appointmentId]
        encounters[appointmentId] = existing ?? `enc-${appointmentId.slice(-3)}`
        if (statuses[appointmentId] !== 'IN_PROGRESS') statuses[appointmentId] = 'IN_PROGRESS'
        return jsonResponse({ encounterId: encounters[appointmentId], appointmentId, patientId: anna.id, practitionerId: PETROV_ID, status: 'IN_PROGRESS', startedAt: '2026-10-20T09:00:00Z', documents: [] }, existing ? 200 : 201)
      }
      if (address.includes('/clinical/patients/')) return jsonResponse([])
      if (address.includes('/status') && method === 'POST') {
        const id = address.split('/appointments/')[1].split('/')[0]
        statuses[id] = JSON.parse(String(init?.body)).status
        return jsonResponse({ id, status: statuses[id] })
      }
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([row(first, '2026-10-20T09:00:00Z', '2026-10-20T09:30:00Z'), row(second, '2026-10-20T10:00:00Z', '2026-10-20T10:30:00Z')])
      return jsonResponse({}, 404)
    }, 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    // 10:00 есть только в ответе API: дожидаемся замены демо-расписания серверным.
    expect(await screen.findByText('10:00')).toBeInTheDocument()

    await user.click(await screen.findByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' }))
    await waitFor(() => expect(statuses[first]).toBe('IN_PROGRESS'))
    await openPage(user, 'Расписание')
    expect(await screen.findByText('На приёме')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Начать приём 10:00 Анна Михайлова' }))
    await waitFor(() => expect(statuses[second]).toBe('IN_PROGRESS'))

    await user.click(await screen.findByRole('button', { name: 'Завершить приём' }))
    await waitFor(() => expect(statuses[second]).toBe('COMPLETED'))
    await openPage(user, 'Расписание')
    expect(await screen.findByText('Завершён')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Начать приём 10:00 Анна Михайлова' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' })).toBeInTheDocument()
    const completion = fetchMock.mock.calls.find(([url, init]) => String(url).includes(`/appointments/${second}/status`) && init?.method === 'POST')
    expect(JSON.parse(String(completion?.[1]?.body))).toEqual({ status: 'COMPLETED' })

    // Повторный старт той же записи открывает уже созданный приём.
    await user.click(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' }))
    await waitFor(() => expect(fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/clinical/encounters') && init?.method === 'POST').length).toBe(3))
    expect(Object.keys(encounters)).toHaveLength(2)
  })
})
