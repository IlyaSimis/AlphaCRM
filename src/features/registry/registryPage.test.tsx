import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth } from '../../test/helpers'

const PETROV = '00000000-0000-0000-0000-000000000201'
const REPEAT = '00000000-0000-0000-0000-000000000602'

describe('registry', () => {
  it('today tasks: the administrator marks one done and creates new ones', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(screen.getByRole('button', { name: 'Регистратура' }))
    expect(screen.getByText('Обновить согласие · Мария Соколова')).toBeInTheDocument()
    expect(screen.getByText('Перезвонить · Дмитрий Волков')).toBeInTheDocument()
    expect(screen.getByText(/Пациентам ничего не отправляется/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выполнено: Обновить согласие · Мария Соколова' }))
    await waitFor(() => expect(screen.queryByText('Обновить согласие · Мария Соколова')).not.toBeInTheDocument())

    // Задача не про пациента тоже в списке на сегодня.
    expect(screen.getByText('Заказать бланки согласий · без пациента')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Новая задача' }))
    await user.click(screen.getByRole('button', { name: 'Создать задачу' }))
    expect(screen.getByText('Напишите, что сделать')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Что сделать'), 'Уточнить у Анны номер полиса')
    await user.click(screen.getByRole('combobox', { name: 'Пациент' }))
    await user.click(screen.getByRole('option', { name: 'Анна Михайлова' }))
    await user.click(screen.getByRole('button', { name: 'Создать задачу' }))
    expect(await screen.findByText('Уточнить у Анны номер полиса · Анна Михайлова')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Новая задача' }))
    await user.type(screen.getByLabelText('Что сделать'), 'Проверить кондиционер в кабинете')
    await user.click(screen.getByRole('button', { name: 'Создать задачу' }))
    expect(await screen.findByText('Проверить кондиционер в кабинете · без пациента')).toBeInTheDocument()
  })

  it('on the server sends free text, assignee and an optional patient', async () => {
    const created: unknown[] = []
    withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/registry/tasks') && method === 'POST') {
        const body = JSON.parse(String(init?.body))
        created.push(body)
        return jsonResponse({ id: `t-${created.length}`, status: 'OPEN', ...body }, 201)
      }
      if (address.includes('/registry/tasks')) return jsonResponse(created.map((body, index) => ({ id: `t-${index + 1}`, status: 'OPEN', ...(body as object) })))
      if (address.includes('/registry/waitlist')) return jsonResponse([])
      if (address.endsWith('/staff')) return jsonResponse([{ id: '00000000-0000-0000-0000-0000000a0001', fullName: 'Администратор', username: 'admin', role: 'ADMINISTRATOR', practitionerId: null, active: true, mustChangePassword: false }])
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Регистратура' }))
    await user.click(screen.getByRole('button', { name: 'Новая задача' }))
    await user.type(screen.getByLabelText('Что сделать'), '  Заказать воду в холл  ')
    await user.click(screen.getByRole('button', { name: 'Создать задачу' }))
    await waitFor(() => expect(created).toHaveLength(1))
    expect(created[0]).toMatchObject({ title: 'Заказать воду в холл', patientId: null, assigneeId: '00000000-0000-0000-0000-0000000a0001' })
    expect(await screen.findByText('Заказать воду в холл · без пациента')).toBeInTheDocument()
    expect(created[0]).not.toHaveProperty('kind')
  })

  it('a cashier sees tasks but not the waitlist; the registry is closed to doctors', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(screen.queryByRole('button', { name: 'Регистратура' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'CASHIER')
    await user.click(screen.getByRole('button', { name: 'Регистратура' }))
    expect(screen.getByText('ЗАДАЧИ НА СЕГОДНЯ')).toBeInTheDocument()
    expect(screen.queryByText('ЛИСТ ОЖИДАНИЯ')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Новая задача' })).not.toBeInTheDocument()
    // Исполнитель закрывает свою задачу, чужую — нет.
    expect(screen.getByRole('button', { name: 'Выполнено: Перезвонить · Дмитрий Волков' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Выполнено: Обновить согласие · Мария Соколова' })).not.toBeInTheDocument()
  })

  it('after a cancellation the free slot offers the waitlist and books a regular appointment', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    expect(screen.queryByRole('button', { name: /Лист ожидания на слот/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Отменить запись 10:10 Дмитрий Волков' }))
    await user.click(await screen.findByRole('button', { name: 'Лист ожидания на слот 2026-10-20 10:10' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('Мария Соколова')).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Записать Мария Соколова на 10:10' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByRole('button', { name: 'Отменить запись 10:10 Мария Соколова' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Лист ожидания на слот/ })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Регистратура' }))
    expect(screen.getByText('Никто не ждёт')).toBeInTheDocument()
  })

  it('on the server books via POST /appointments and closes the waitlist entry with its id', async () => {
    const cancelledId = '00000000-0000-0000-0000-000000000399'
    const createdId = '00000000-0000-0000-0000-000000000398'
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/registry/waitlist/801/book')) return jsonResponse({ id: '801', status: 'BOOKED', appointmentId: createdId })
      if (address.includes('/registry/waitlist')) return jsonResponse([{ id: '801', patientId: anna.id, practitionerId: PETROV, serviceId: REPEAT, wishFrom: '2026-10-19', wishTo: '2026-10-30', status: 'ACTIVE', appointmentId: null }])
      if (address.includes('/registry/tasks')) return jsonResponse([])
      if (address.includes('/appointments') && method === 'POST') return jsonResponse({ id: createdId, ...JSON.parse(String(init?.body)), status: 'BOOKED' }, 201)
      if (address.includes('/appointments')) return jsonResponse([{ id: cancelledId, patientId: anna.id, practitionerId: PETROV, startsAt: '2026-10-20T12:00:00Z', endsAt: '2026-10-20T12:30:00Z', serviceName: 'Повторный приём', status: 'CANCELLED' }])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Лист ожидания на слот 2026-10-20 12:00' }))
    await user.click(screen.getByRole('button', { name: 'Записать Анна Михайлова на 12:00' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/waitlist/801/book'))).toBe(true))
    const created = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/appointments') && init?.method === 'POST')
    expect(JSON.parse(String(created?.[1]?.body))).toMatchObject({ patientId: anna.id, practitionerId: PETROV, startsAt: '2026-10-20T12:00:00.000Z', serviceName: 'Повторный приём' })
    const booked = fetchMock.mock.calls.find(([url]) => String(url).includes('/waitlist/801/book'))
    expect(JSON.parse(String(booked?.[1]?.body))).toEqual({ appointmentId: createdId })
  })
})
