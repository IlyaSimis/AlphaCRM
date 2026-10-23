import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth } from '../../test/helpers'

describe('patients', () => {
  it('opens the patient list and the selected person card', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    expect(screen.getByRole('heading', { name: 'Поиск пациента' })).toBeInTheDocument()
    await user.click(screen.getByText('Дмитрий Волков'))
    expect(screen.getByRole('heading', { name: 'Дмитрий Волков' })).toBeInTheDocument()
    expect(screen.getByText('+7 903 782-44-16')).toBeInTheDocument()
  })

  it('adds a patient to the list', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByRole('button', { name: /новый пациент/i }))
    await user.clear(screen.getByLabelText('ФИО'))
    await user.type(screen.getByLabelText('ФИО'), 'Ирина Белова')
    await user.click(screen.getByRole('button', { name: /сохранить пациента/i }))
    expect(await screen.findByText('Ирина Белова')).toBeInTheDocument()
  })

  it('the administrator edits patient details', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Дмитрий Волков'))
    await user.click(screen.getByRole('button', { name: 'Редактировать' }))
    await user.clear(screen.getByLabelText('Телефон'))
    await user.type(screen.getByLabelText('Телефон'), '+7 900 000-00-01')
    await user.click(screen.getByRole('button', { name: 'Сохранить изменения' }))
    expect(await screen.findByText('+7 900 000-00-01')).toBeInTheDocument()
    expect(screen.getByText('Демо-режим: изменения видны только до обновления страницы')).toBeInTheDocument()
  })

  it('shows all patient appointments in the card without paging by day', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Дмитрий Волков'))
    expect(screen.getByText('Ближайшие записи')).toBeInTheDocument()
    expect(screen.getByText('Прошедшие записи')).toBeInTheDocument()
    expect(screen.getByText('20.10.2026 10:10 · Подтверждён')).toBeInTheDocument()
    expect(screen.getByText('06.10.2026 10:10 · Завершён')).toBeInTheDocument()
    expect(screen.getByText('08.10.2026 09:00 · Отменена')).toBeInTheDocument()
    expect(screen.getByText('13.10.2026 11:00 · Завершён')).toBeInTheDocument()
  })

  it('the administrator renews and revokes consent', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Мария Соколова'))
    expect(screen.getByText('Требует обновления')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Обновить согласие' }))
    await user.click(screen.getByRole('button', { name: 'Сохранить согласие' }))
    expect(await screen.findByText('Согласие действует')).toBeInTheDocument()
    expect(screen.getByText(/отозвано/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Отозвать согласие v1.3' }))
    expect(await screen.findByText('Требует обновления')).toBeInTheDocument()
  })

  it('shows the server refusal instead of clinical history without a care relationship', async () => {
    withAuth(async (url: string) => {
      const address = String(url)
      if (address.includes('/clinical/patients/')) return jsonResponse({ code: 'NO_CARE_RELATIONSHIP', message: 'No active care relationship with the patient' }, 403)
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Анна Михайлова'))
    expect(await screen.findByText('Нет активной связи с лечением пациента')).toBeInTheDocument()
    expect(screen.queryByText('История приёмов')).not.toBeInTheDocument()
  })

  it('shows a doctor the visit history with document versions', async () => {
    withAuth(async (url: string) => {
      const address = String(url)
      if (address.includes('/clinical/patients/')) {
        return jsonResponse([{
          encounterId: 'e1', appointmentId: 'a1', patientId: anna.id, practitionerId: '00000000-0000-0000-0000-000000000201', status: 'IN_PROGRESS', startedAt: '2026-10-20T09:05:00Z',
          diagnosis: 'Синтетический диагноз', documents: [
            { id: 'd1', state: 'SIGNED_LOCKED', templateVersion: 'v2.4', signedAt: '2026-10-20T10:06:00Z', amendsDocumentId: null },
            { id: 'd2', state: 'DRAFT', templateVersion: 'v2.4', signedAt: null, amendsDocumentId: 'd1' },
          ],
        }])
      }
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 't.petrov')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Анна Михайлова'))
    expect(await screen.findByText('История приёмов')).toBeInTheDocument()
    expect(screen.getByText(/Документ приёма · подписан/)).toBeInTheDocument()
    expect(screen.getByText('Дополнение · черновик')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Открыть' }))
    expect(await screen.findByLabelText('Диагноз')).toHaveValue('Синтетический диагноз')
    expect(screen.getByText('Дополнение · черновик', { selector: '.MuiChip-label' })).toBeInTheDocument()
  })

  it('names the staff member own role in the lock message', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'MANAGER')
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText(/Роль «Менеджер» не имеет доступа/)).toBeInTheDocument()
  })

  it('hides clinical data from the administrator', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(screen.getByRole('button', { name: 'Пациенты' }))
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText('Клинические данные недоступны')).toBeInTheDocument()
    expect(screen.getByText(/Роль «Администратор» не имеет доступа/)).toBeInTheDocument()
    expect(screen.queryByText(/Тревожно-депрессивная/)).not.toBeInTheDocument()
  })
})
