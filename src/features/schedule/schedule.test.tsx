import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { anna, chooseRole, goOffline, jsonResponse, renderApp } from '../../test/helpers'

describe('расписание', () => {
  it('создаёт запись из расписания', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Новая запись')
    await waitFor(() => expect(screen.getByLabelText('Время')).toHaveValue('13:00'))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '15:45' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('15:45')).toBeInTheDocument()
    expect(screen.getByText('Демо-режим: запись видна только до обновления страницы')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('оставляет форму открытой, если выбранное время уже занято', async () => {
    let appointments: Array<Record<string, string>> = []
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      if (String(url).includes('/patients')) return jsonResponse([anna])
      if (String(url).includes('/appointments') && method === 'POST') {
        const body = JSON.parse(String(init?.body))
        if (appointments.some((item) => item.startsAt === body.startsAt)) {
          return jsonResponse({ code: 'SLOT_ALREADY_BOOKED', message: 'Интервал врача уже занят' }, 409)
        }
        const created = { id: 'appt-1', ...body, status: 'BOOKED', version: 0 }
        appointments = [...appointments, created]
        return jsonResponse(created, 201)
      }
      if (String(url).includes('/appointments')) return jsonResponse(appointments)
      return jsonResponse({}, 404)
    }))
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

  it('переключает день расписания и показывает запись на выбранную дату', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
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

  it('после обновления оставляет день записи и отмену администратора', async () => {
    let appointments: Array<Record<string, string>> = []
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
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
        const date = new URL(address).searchParams.get('date')
        return jsonResponse(appointments.filter((item) => String(item.startsAt).startsWith(`${date}T`)))
      }
      return jsonResponse({}, 404)
    }))
    const user = userEvent.setup()
    const first = renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await chooseRole(user, 'Администратор')
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    fireEvent.change(screen.getByLabelText('Дата'), { target: { value: '2026-10-22' } })
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '15:05' } })
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(await screen.findByText('15:05')).toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-22')
    expect(screen.getByText('22 октября 2026, четверг')).toBeInTheDocument()
    first.unmount()

    renderApp()
    expect(await screen.findByText('15:05')).toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toHaveValue('2026-10-22')
    expect(screen.getByRole('button', { name: 'Отменить запись 15:05 Анна Михайлова' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Сегодня' }))
    const now = new Date()
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    expect(screen.getByLabelText('День расписания')).toHaveValue(today)
    if (today !== '2026-10-22') expect(screen.queryByText('15:05')).not.toBeInTheDocument()
  })

  it('отменяет запись только у администратора', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    expect(screen.queryByRole('button', { name: /отменить запись/i })).not.toBeInTheDocument()
    await chooseRole(user, 'Администратор')
    await user.click(screen.getByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' }))
    expect(await screen.findByText('Демо-режим: отмена видна только до обновления страницы')).toBeInTheDocument()
    expect(screen.getByText('Отменена')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Отменить запись 09:00 Анна Михайлова' })).toBeInTheDocument()
  })

  it('меняет статус записи: администратор отмечает приход и неявку', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    expect(screen.queryByRole('button', { name: 'Изменить статус 11:00 Мария Соколова' })).not.toBeInTheDocument()
    await chooseRole(user, 'Администратор')
    await user.click(screen.getByRole('button', { name: 'Изменить статус 11:00 Мария Соколова' }))
    await user.click(screen.getByRole('menuitem', { name: 'Отметить неявку' }))
    expect(await screen.findByText('Неявка')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Отменить запись 11:00 Мария Соколова' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Изменить статус 10:10 Дмитрий Волков' }))
    await user.click(screen.getByRole('menuitem', { name: 'Пациент пришёл' }))
    expect(await screen.findAllByText('Прибыл')).toHaveLength(2)
  })

  it('отправляет смену статуса на сервер от имени врача', async () => {
    let status = 'ARRIVED'
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/status')) {
        status = JSON.parse(String(init?.body)).status
        return jsonResponse({ status })
      }
      if (address.includes('/appointments')) return jsonResponse([{ id: '00000000-0000-0000-0000-000000000301', patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-20T09:00:00Z', endsAt: '2026-10-20T09:50:00Z', serviceName: 'Первичный приём', status }])
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Изменить статус 09:00 Анна Михайлова' }))
    await user.click(screen.getByRole('menuitem', { name: 'Завершить приём' }))
    expect(await screen.findByText('Завершён')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('/status'))
    expect(call?.[1]?.headers).toMatchObject({ 'X-Demo-Role': 'DOCTOR', 'X-Actor-Id': '00000000-0000-0000-0000-000000000201' })
  })

  it('показывает расписание нескольких врачей и фильтрует по врачу', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    expect(screen.getByText('Ольга Лебедева')).toBeInTheDocument()
    expect(screen.getByText('Ксения Орлова')).toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: 'Врач' }))
    await user.click(screen.getByRole('option', { name: 'Ольга Лебедева' }))
    expect(screen.getByText('Ксения Орлова')).toBeInTheDocument()
    expect(screen.queryByText('Дмитрий Волков')).not.toBeInTheDocument()
  })

  it('врач работает от имени выбранного врача: приём и статусы только по его записям', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    expect(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' })).toBeInTheDocument()
    await user.click(screen.getByLabelText('Работать от имени врача'))
    await user.click(screen.getByRole('option', { name: 'Ольга Лебедева' }))
    expect(screen.queryByRole('button', { name: /начать приём/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Изменить статус 09:00 Анна Михайлова' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Ольга Лебедева').length).toBeGreaterThan(1)
  })

  it('отправляет действие врача с X-Actor-Id выбранного врача', async () => {
    let status = 'ARRIVED'
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/status')) {
        status = JSON.parse(String(init?.body)).status
        return jsonResponse({ status })
      }
      if (address.includes('/appointments')) return jsonResponse([{ id: '00000000-0000-0000-0000-000000000512', patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000202', startsAt: '2026-10-20T15:00:00Z', endsAt: '2026-10-20T15:30:00Z', serviceName: 'Повторный приём', status }])
      return jsonResponse({}, 404)
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByLabelText('Работать от имени врача'))
    await user.click(screen.getByRole('option', { name: 'Ольга Лебедева' }))
    await user.click(await screen.findByRole('button', { name: 'Изменить статус 15:00 Анна Михайлова' }))
    await user.click(screen.getByRole('menuitem', { name: 'Завершить приём' }))
    expect(await screen.findByText('Завершён')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url]) => String(url).includes('/status'))
    expect(call?.[1]?.headers).toMatchObject({ 'X-Demo-Role': 'DOCTOR', 'X-Actor-Id': '00000000-0000-0000-0000-000000000202' })
  })

  it('не отправляет запись вне рабочих часов врача и показывает ошибку', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('offline'))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp()
    await user.click(screen.getByRole('button', { name: /новая запись/i }))
    fireEvent.change(screen.getByLabelText('Время'), { target: { value: '19:00' } })
    expect(screen.getByText('Время вне рабочих часов врача')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /создать запись/i }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Время вне рабочих часов врача')
    expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false)
  })

  it('показывает текст ответа сервера при ошибке переноса', async () => {
    vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
      const address = String(url)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/reschedule')) return jsonResponse({ code: 'SLOT_ALREADY_BOOKED', message: 'Интервал врача уже занят' }, 409)
      if (address.includes('/appointments') && (init?.method ?? 'GET') === 'GET') return jsonResponse([{ id: '00000000-0000-0000-0000-000000000302', patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', startsAt: '2026-10-20T10:10:00Z', endsAt: '2026-10-20T10:40:00Z', serviceName: 'Повторный приём', status: 'CONFIRMED' }])
      return jsonResponse({}, 404)
    }))
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await chooseRole(user, 'Администратор')
    await user.click(await screen.findByRole('button', { name: 'Перенести запись 10:10 Анна Михайлова' }))
    fireEvent.change(screen.getByLabelText('Новое время'), { target: { value: '11:00' } })
    await user.click(screen.getByRole('button', { name: 'Перенести' }))
    expect(await screen.findByText('Интервал врача уже занят')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('администратор переносит запись в демо-режиме', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await chooseRole(user, 'Администратор')
    await user.click(screen.getByRole('button', { name: 'Перенести запись 11:00 Мария Соколова' }))
    fireEvent.change(screen.getByLabelText('Новое время'), { target: { value: '16:00' } })
    await user.click(screen.getByRole('button', { name: 'Перенести' }))
    expect(await screen.findByText('Демо-режим: перенос виден только до обновления страницы')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Перенести запись 16:00 Мария Соколова' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Перенести запись 11:00 Мария Соколова' })).not.toBeInTheDocument()
  })
})
