import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TEST_CSRF, anna, chooseRole, enterDemo, goOffline, jsonResponse, openSchedule, renderApp, signIn, withAuth } from '../../test/helpers'

describe('schedule', () => {
  it('books an appointment from the schedule', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Новая запись')
    await waitFor(() => expect(screen.getByLabelText('Время')).toHaveValue('13:00'))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '15:45' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('15:45')).toBeInTheDocument()
    expect(screen.getByText('Демо-режим: запись видна только до обновления страницы')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('keeps the form open when the chosen time is taken', async () => {
    let appointments: Array<Record<string, string>> = []
    withAuth(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (String(url).includes('/patients')) return jsonResponse([anna])
      if (String(url).includes('/appointments') && method === 'POST') {
        const body = JSON.parse(String(init?.body))
        if (appointments.some((item) => item.startsAt === body.startsAt)) {
          return jsonResponse({ code: 'SLOT_ALREADY_BOOKED', message: "The practitioner's slot is already booked" }, 409)
        }
        const created = { id: 'appt-1', ...body, status: 'BOOKED', version: 0 }
        appointments = [...appointments, created]
        return jsonResponse(created, 201)
      }
      if (String(url).includes('/appointments')) return jsonResponse(appointments)
      return jsonResponse({}, 404)
    }, 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    await waitFor(() => expect(screen.getByLabelText('Время')).toHaveValue('13:00'))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '16:10' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('16:10')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    await waitFor(() => expect(screen.getByLabelText('Время')).toHaveValue('13:00'))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '16:10' } })
    expect(screen.getByLabelText('Время')).toHaveValue('16:10')
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('Интервал врача уже занят')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getAllByText('16:10')).toHaveLength(1)
  })

  it('switches the schedule day and shows the appointment on the chosen date', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(screen.getByText('Анна Михайлова')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Следующий день' }))
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-21')
    expect(screen.queryByText('Анна Михайлова')).not.toBeInTheDocument()
    expect(screen.getByText('На этот день записей нет')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    fireEvent.change(screen.getByLabelText('Дата'), { target: { value: '2026-10-22' } })
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '14:20' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('14:20')).toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-22')
    expect(screen.queryByText('Дмитрий Волков')).not.toBeInTheDocument()
  })

  it('after a reload keeps the appointment day and the administrator cancellation', async () => {
    let appointments: Array<Record<string, string>> = []
    withAuth(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      const address = String(url)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/cancel')) return jsonResponse({}, 404)
      if (address.includes('/appointments') && method === 'POST') {
        const body = JSON.parse(String(init?.body))
        const created = { id: '00000000-0000-0000-0000-000000000399', ...body, status: 'BOOKED', version: 0 }
        appointments = [...appointments, created]
        return jsonResponse(created, 201)
      }
      if (address.includes('/appointments')) {
        const date = new URL(address, 'http://localhost').searchParams.get('date')
        return jsonResponse(appointments.filter((item) => String(item.startsAt).startsWith(`${date}T`)))
      }
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    const first = renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openSchedule(user)
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    fireEvent.change(screen.getByLabelText('Дата'), { target: { value: '2026-10-22' } })
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '15:05' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('15:05')).toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-22')
    expect(screen.getByText('22 октября 2026, четверг')).toBeInTheDocument()
    first.unmount()

    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openSchedule(user)
    expect(await screen.findByText('15:05')).toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-22')
    expect(screen.getByRole('button', { name: 'Отменить запись 15:05 Анна Михайлова' })).toBeInTheDocument()
    // «Сегодня» есть и в меню, и в расписании — нажимаем кнопку расписания.
    await user.click(within(document.querySelector('.content') as HTMLElement).getByRole('button', { name: 'Сегодня' }))
    const now = new Date()
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    expect(screen.getByLabelText('День расписания')).toHaveValue(today)
    if (today !== '2026-10-22') expect(screen.queryByText('15:05')).not.toBeInTheDocument()
  })

  it('only the administrator cancels an appointment', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(screen.queryByRole('button', { name: /отменить запись/i })).not.toBeInTheDocument()
    await chooseRole(user, 'ADMINISTRATOR')
    await openSchedule(user)
    await user.click(screen.getByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' }))
    expect(await screen.findByText('Демо-режим: отмена видна только до обновления страницы')).toBeInTheDocument()
    expect(screen.getByText('Отменена')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Отменить запись 09:00 Анна Михайлова' })).toBeInTheDocument()
  })

  it('changes appointment status: the administrator marks arrival and no-show', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(screen.queryByRole('button', { name: 'Изменить статус 11:00 Мария Соколова' })).not.toBeInTheDocument()
    await chooseRole(user, 'ADMINISTRATOR')
    await openSchedule(user)
    await user.click(screen.getByRole('button', { name: 'Изменить статус 11:00 Мария Соколова' }))
    await user.click(screen.getByRole('menuitem', { name: 'Отметить неявку' }))
    expect(await screen.findByText('Неявка')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отменить запись 11:00 Мария Соколова' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Изменить статус 10:10 Дмитрий Волков' }))
    await user.click(screen.getByRole('menuitem', { name: 'Пациент пришёл' }))
    expect(await screen.findAllByText('Прибыл')).toHaveLength(2)
  })

  it('sends a status change to the server as the doctor', async () => {
    let status = 'ARRIVED'
    const fetchMock = withAuth(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/status')) {
        status = JSON.parse(String(init?.body)).status
        return jsonResponse({ status })
      }
      if (address.includes('/appointments')) return jsonResponse([{ id: '00000000-0000-0000-0000-000000000301', patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-20T09:00:00Z', endsAt: '2026-10-20T09:50:00Z', serviceName: 'Серверный приём', status }])
      return jsonResponse({}, 404)
    }, 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(await screen.findByText('Серверный приём')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Изменить статус 09:00 Анна Михайлова' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Завершить приём' }))
    expect(await screen.findByText('Завершён')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('/status'))
    // Роль и автор — из сессии на сервере; клиент шлёт только cookie и CSRF-заголовок.
    expect(call?.[1]?.credentials).toBe('include')
    expect(call?.[1]?.headers).toMatchObject({ 'X-XSRF-TOKEN': TEST_CSRF })
    expect(call?.[1]?.headers).not.toHaveProperty('X-Demo-Role')
  })

  it('shows the schedule of several doctors and filters by doctor', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(screen.getByText('Ольга Лебедева')).toBeInTheDocument()
    expect(screen.getByText('Ксения Орлова')).toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: 'Врач' }))
    await user.click(screen.getByRole('option', { name: 'Ольга Лебедева' }))
    expect(screen.getByText('Ксения Орлова')).toBeInTheDocument()
    expect(screen.queryByText('Дмитрий Волков')).not.toBeInTheDocument()
  })

  it('a doctor sees visit and status actions only for own appointments, with no doctor switch', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Работать от имени врача')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Роль пользователя')).not.toBeInTheDocument()
    // Чужая запись (врач Лебедева) — без приёма и статусов.
    expect(screen.queryByRole('button', { name: 'Начать приём 15:00 Ксения Орлова' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Изменить статус 15:00 Ксения Орлова' })).not.toBeInTheDocument()
  })

  it('doctor Lebedeva signs in with her own login and changes the status of her appointment', async () => {
    let status = 'ARRIVED'
    const fetchMock = withAuth(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/status')) {
        status = JSON.parse(String(init?.body)).status
        return jsonResponse({ status })
      }
      if (address.includes('/appointments')) return jsonResponse([{ id: '00000000-0000-0000-0000-000000000512', patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000202', startsAt: '2026-10-20T15:00:00Z', endsAt: '2026-10-20T15:30:00Z', serviceName: 'Повторный приём', status }])
      return jsonResponse({}, 404)
    }, null)
    const user = userEvent.setup()
    renderApp()
    await signIn(user, 't.lebedeva')
    expect((await screen.findAllByText('API подключён')).length).toBeGreaterThan(0)
    expect(screen.getByText('Ольга Лебедева', { selector: '.profile *' })).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Изменить статус 15:00 Анна Михайлова' }))
    await user.click(await screen.findByRole('menuitem', { name: 'Завершить приём' }))
    expect(await screen.findByText('Завершён')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('/status'))
    expect(call?.[1]?.headers).not.toHaveProperty('X-Actor-Id')
  })

  it('does not send a booking outside working hours and shows the error', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('offline'))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '19:00' } })
    expect(screen.getByText('Время вне рабочих часов врача')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Время вне рабочих часов врача')
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false)
  })

  it('on the server does not offer a time that has already passed', async () => {
    const fetchMock = withAuth(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments') && init?.method !== 'POST') return jsonResponse([])
      return jsonResponse({}, 404)
    })
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openSchedule(user)
    // «Сейчас» в тестах — 20.10.2026 06:00 UTC: 05:00 того же дня уже прошло.
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    fireEvent.change(screen.getByLabelText('Дата'), { target: { value: '2026-10-13' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Это время уже прошло')
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/appointments') && init?.method === 'POST')).toBe(false)
  })

  it('shows the server answer on a reschedule error', async () => {
    withAuth(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/reschedule')) return jsonResponse({ code: 'SLOT_ALREADY_BOOKED', message: "The practitioner's slot is already booked" }, 409)
      if (address.includes('/appointments') && (init?.method ?? 'GET') === 'GET') return jsonResponse([{ id: '00000000-0000-0000-0000-000000000302', patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-20T10:10:00Z', endsAt: '2026-10-20T10:40:00Z', serviceName: 'Повторный приём', status: 'CONFIRMED' }])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openSchedule(user)
    await user.click(await screen.findByRole('button', { name: 'Перенести запись 10:10 Анна Михайлова' }))
    fireEvent.change(screen.getByLabelText('Новое время'), { target: { value: '11:00' } })
    await user.click(screen.getByRole('button', { name: 'Перенести' }))
    expect(await screen.findByText('Интервал врача уже занят')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('the administrator reschedules an appointment in demo mode', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openSchedule(user)
    await user.click(screen.getByRole('button', { name: 'Перенести запись 11:00 Мария Соколова' }))
    fireEvent.change(screen.getByLabelText('Новое время'), { target: { value: '16:00' } })
    await user.click(screen.getByRole('button', { name: 'Перенести' }))
    expect(await screen.findByText('Демо-режим: перенос виден только до обновления страницы')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Перенести запись 16:00 Мария Соколова' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Перенести запись 11:00 Мария Соколова' })).not.toBeInTheDocument()
  })
})
