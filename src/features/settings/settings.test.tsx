import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth } from '../../test/helpers'

describe('working hours and prices', () => {
  it('the administrator makes Wednesday a working day and the week stops showing it as a day off', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(screen.getByRole('button', { name: 'График и цены' }))
    await user.click(screen.getAllByRole('checkbox', { name: 'Среда' })[0])
    await user.click(screen.getByRole('button', { name: 'Сохранить график Александр Петров' }))
    expect(await screen.findByText('Демо-режим: график виден только до обновления страницы')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Расписание' }))
    await user.click(screen.getByRole('button', { name: 'Неделя' }))
    const wednesday = screen.getByRole('group', { name: 'Александр Петров, Ср 21.10' })
    expect(within(wednesday).getByText('09:00–18:00')).toBeInTheDocument()
    expect(within(wednesday).getByRole('button', { name: '+ Запись' })).toBeEnabled()
  })

  it('a new price does not rewrite the price snapshot of existing appointments', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(screen.getByRole('button', { name: 'График и цены' }))
    const price = screen.getByLabelText('Цена Повторный приём, рублей')
    await user.clear(price)
    await user.type(price, '3500')
    await user.click(screen.getByRole('button', { name: 'Сохранить цену Повторный приём' }))
    expect(await screen.findByText(/сейчас 3\s?500,00/)).toBeInTheDocument()

    // Завершённый ранее повторный приём в кассе остаётся по старой цене 3 000 ₽.
    await user.click(screen.getByRole('button', { name: 'Касса' }))
    expect(screen.getAllByText(/3\s000,00/).length).toBeGreaterThan(0)
    expect(screen.queryByText(/3\s500,00/)).not.toBeInTheDocument()
  })

  it('on the server sends weekly hours and price as the administrator only', async () => {
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.includes('/working-hours') && method === 'PUT') return jsonResponse({ id: '00000000-0000-0000-0000-000000000201', fullName: 'Александр Петров', specialty: 'Психиатр', workingHours: JSON.parse(String(init?.body)).shifts })
      if (address.includes('/price') && method === 'PUT') return jsonResponse({ id: '00000000-0000-0000-0000-000000000602', name: 'Повторный приём', durationMinutes: 30, priceMinor: JSON.parse(String(init?.body)).priceMinor, currency: 'RUB' })
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'График и цены' }))
    await user.click(screen.getAllByRole('checkbox', { name: 'Вторник' })[0])
    await user.click(screen.getByRole('button', { name: 'Сохранить график Александр Петров' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/working-hours'))).toBe(true))
    const hours = fetchMock.mock.calls.find(([url]) => String(url).includes('/working-hours'))
    expect(JSON.parse(String(hours?.[1]?.body))).toEqual({ shifts: [{ dayOfWeek: 4, startsAt: '09:00', endsAt: '18:00' }] })

    const price = screen.getByLabelText('Цена Повторный приём, рублей')
    await user.clear(price)
    await user.type(price, '3200,50')
    await user.click(screen.getByRole('button', { name: 'Сохранить цену Повторный приём' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/price'))).toBe(true))
    const saved = fetchMock.mock.calls.find(([url]) => String(url).includes('/price'))
    expect(JSON.parse(String(saved?.[1]?.body))).toEqual({ priceMinor: 320050 })
  })

  it('manager and cashier see neither hours, prices nor staff', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'MANAGER')
    expect(screen.queryByRole('button', { name: 'График и цены' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Сотрудники' })).not.toBeInTheDocument()
  })

  it('the administrator adds a service, it is bookable at once; a switched-off one is not offered', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(screen.getByRole('button', { name: 'График и цены' }))
    await user.click(screen.getByRole('button', { name: 'Добавить услугу' }))
    expect(screen.getByText('Укажите название услуги')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Название услуги'), 'Семейная консультация')
    await user.type(screen.getByLabelText('Длительность, мин'), '80')
    await user.type(screen.getByLabelText('Цена новой услуги, ₽'), '7000')
    await user.click(screen.getByRole('button', { name: 'Добавить услугу' }))
    expect(await screen.findByLabelText('Цена Семейная консультация, рублей')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выключить услугу Повторный приём' }))
    expect(await screen.findByText('Выключена: не предлагается в новой записи')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Расписание' }))
    await user.click(screen.getByRole('button', { name: 'Новая запись' }))
    await user.click(screen.getByRole('combobox', { name: 'Услуга' }))
    const options = screen.getAllByRole('option').map((option) => option.textContent ?? '')
    expect(options.some((text) => text.startsWith('Семейная консультация · 80 мин'))).toBe(true)
    expect(options.some((text) => text.startsWith('Повторный приём'))).toBe(false)
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: 'Отмена' }))
    // Старые записи на выключенную услугу остаются в расписании со своим названием.
    expect(screen.getAllByText(/Повторный приём/).length).toBeGreaterThan(0)
  })

  it('on the server creates and switches off services; booking takes the list from the server', async () => {
    const family = { id: '00000000-0000-0000-0000-000000000699', name: 'Семейная консультация', durationMinutes: 80, priceMinor: 700000, currency: 'RUB', active: true }
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/services') && method === 'POST') return jsonResponse({ ...family, ...JSON.parse(String(init?.body)) }, 201)
      if (address.endsWith('/deactivate') && method === 'POST') return jsonResponse({ id: '00000000-0000-0000-0000-000000000602', name: 'Повторный приём', durationMinutes: 30, priceMinor: 300000, currency: 'RUB', active: false })
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin', { services: [{ id: '00000000-0000-0000-0000-000000000602', name: 'Повторный приём', durationMinutes: 30, priceMinor: 300000, currency: 'RUB', active: true }] })
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/services?all=true'))).toBe(true)
    await user.click(screen.getByRole('button', { name: 'График и цены' }))
    // Первичного приёма на этом сервере нет: зашитого списка услуг в интерфейсе нет.
    expect(screen.queryByText('Первичный приём')).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Название услуги'), ' Семейная консультация ')
    await user.type(screen.getByLabelText('Длительность, мин'), '80')
    await user.type(screen.getByLabelText('Цена новой услуги, ₽'), '7000')
    await user.click(screen.getByRole('button', { name: 'Добавить услугу' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/services') && init?.method === 'POST')).toBe(true))
    const created = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/services') && init?.method === 'POST')
    expect(JSON.parse(String(created?.[1]?.body))).toEqual({ name: 'Семейная консультация', durationMinutes: 80, priceMinor: 700000, currency: 'RUB' })

    await user.click(screen.getByRole('button', { name: 'Выключить услугу Повторный приём' }))
    expect(await screen.findByRole('button', { name: 'Включить услугу Повторный приём' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/services/00000000-0000-0000-0000-000000000602/deactivate'))).toBe(true)
  })

  it('shows the on-screen phrase for a known error code', async () => {
    withAuth((address, init) => {
      if (address.endsWith('/services') && init?.method === 'POST') return jsonResponse({ code: 'SERVICE_NAME_TAKEN', message: 'A service with this name already exists' }, 409)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin', { services: [] })
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'График и цены' }))
    expect(screen.getByText(/Услуг пока нет/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Название услуги'), 'Тест')
    await user.type(screen.getByLabelText('Длительность, мин'), '30')
    await user.type(screen.getByLabelText('Цена новой услуги, ₽'), '100')
    await user.click(screen.getByRole('button', { name: 'Добавить услугу' }))
    expect(await screen.findByText('Услуга с таким названием уже есть')).toBeInTheDocument()
    expect(screen.queryByText('SERVICE_NAME_TAKEN')).not.toBeInTheDocument()
  })
})
