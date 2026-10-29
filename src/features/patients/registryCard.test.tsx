import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth, openPage } from '../../test/helpers'

describe('patient card: registry', () => {
  it('the administrator changes the referral source and note; a doctor does not see them', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    expect(await screen.findByText('Рекомендация знакомых')).toBeInTheDocument()
    expect(screen.getByText('Просит звонить после 18:00')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Изменить' }))
    await user.click(screen.getByRole('combobox', { name: 'Откуда пришёл' }))
    await user.click(screen.getByRole('option', { name: 'Сайт клиники' }))
    await user.clear(screen.getByLabelText('Заметка регистратуры'))
    await user.type(screen.getByLabelText('Заметка регистратуры'), 'Удобно по утрам')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))
    expect(await screen.findByText('Сайт клиники')).toBeInTheDocument()
    expect(screen.getByText('Удобно по утрам')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user)
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.queryByText('Удобно по утрам')).not.toBeInTheDocument()
    expect(screen.queryByText('Задачи регистратуры')).not.toBeInTheDocument()
  })

  it('shows patient tasks and adds a new one from the card', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Мария Соколова'))
    expect(await screen.findByRole('button', { name: 'Выполнено: Обновить согласие' })).toBeInTheDocument()
    // Задача без пациента в карточку не попадает.
    expect(screen.queryByText(/Заказать бланки согласий/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Задача' }))
    // Пациент уже выбран — в форме из карточки его не спрашивают.
    expect(screen.queryByRole('combobox', { name: 'Пациент' })).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('Что сделать'), 'Перезвонить после 18:00')
    await user.click(screen.getByRole('button', { name: 'Создать задачу' }))
    expect(await screen.findByRole('button', { name: 'Выполнено: Перезвонить после 18:00' })).toBeInTheDocument()
  })

  it('warns that the phone is already on another card', async () => {
    const boris = { ...anna, id: '00000000-0000-0000-0000-000000000104', fullName: 'Борис Михайлов' }
    const fetchMock = withAuth((address, init) => {
      if (address.endsWith(`/patients/${anna.id}/registry`) && init?.method === 'PUT') return jsonResponse({ ...anna, ...JSON.parse(String(init.body)), phoneSharedWith: [{ id: boris.id, fullName: boris.fullName }] })
      if (address.includes('/registry/tasks')) return jsonResponse([])
      if (address.includes('/patients')) return jsonResponse([{ ...anna, phoneSharedWith: [{ id: boris.id, fullName: boris.fullName }] }, { ...boris, phoneSharedWith: [{ id: anna.id, fullName: anna.fullName }] }])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText(/Этот телефон уже указан в другой карточке/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Борис Михайлов' }))
    expect(screen.getByRole('heading', { name: 'Борис Михайлов' })).toBeInTheDocument()

    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    await user.click(screen.getByRole('button', { name: 'Изменить' }))
    await user.type(screen.getByLabelText('Заметка регистратуры'), 'Только для регистратуры')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/registry') && init?.method === 'PUT')).toBe(true))
    const call = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/registry') && init?.method === 'PUT')
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ referralSource: null, registryNote: 'Только для регистратуры', telegramChatId: null, removeTelegram: false })
  })

  it('the administrator connects Telegram by chat id; the chat id is never shown', async () => {
    let connected = false
    const fetchMock = withAuth((address, init) => {
      if (address.endsWith(`/patients/${anna.id}/registry`) && init?.method === 'PUT') {
        connected = Boolean(JSON.parse(String(init.body)).telegramChatId)
        return jsonResponse({ ...anna, telegramConnected: connected })
      }
      if (address.includes('/registry/tasks')) return jsonResponse([])
      if (address.includes('/patients')) return jsonResponse([{ ...anna, telegramConnected: connected }])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText('не подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Изменить' }))
    await user.type(screen.getByLabelText('Telegram chat id'), '+7 916')
    expect(screen.getByText(/только цифры/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Сохранить' })).toBeDisabled()
    await user.clear(screen.getByLabelText('Telegram chat id'))
    await user.type(screen.getByLabelText('Telegram chat id'), '987654321')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))
    expect(await screen.findByText('подключён')).toBeInTheDocument()
    expect(screen.queryByText('987654321')).not.toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/registry') && init?.method === 'PUT')
    expect(JSON.parse(String(call?.[1]?.body))).toMatchObject({ telegramChatId: '987654321', removeTelegram: false })
  })
})
