import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, withAuth, openPage } from '../../test/helpers'

describe('patients', () => {
  it('opens the patient list and the selected person card', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    await openPage(user, 'Пациенты')
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
    await openPage(user, 'Пациенты')
    await user.click(screen.getByRole('button', { name: /новый пациент/i }))
    // Новая карточка открывается пустой: без чужого имени, даты и телефона.
    expect(screen.getByLabelText('ФИО')).toHaveValue('')
    expect(screen.getByLabelText('Дата рождения')).toHaveValue('')
    expect(screen.getByLabelText('Телефон')).toHaveValue('')
    expect(screen.getByLabelText('Второй телефон')).toHaveValue('')
    expect(screen.getByLabelText('E-mail')).toHaveValue('')
    expect(screen.getByRole('button', { name: /сохранить пациента/i })).toBeDisabled()
    await user.type(screen.getByLabelText('ФИО'), 'Ирина Белова')
    fireEvent.change(screen.getByLabelText('Дата рождения'), { target: { value: '1994-03-12' } })
    await user.type(screen.getByLabelText('Телефон'), '+7 903 111-22-33')
    await user.click(screen.getByRole('button', { name: /сохранить пациента/i }))
    expect(await screen.findByText('Ирина Белова')).toBeInTheDocument()
  })

  it('e-mail is checked; the second phone warns about a duplicate; the card shows the new fields', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Дмитрий Волков'))
    await user.click(screen.getByRole('button', { name: 'Редактировать' }))
    // Правка открывается с данными карточки.
    expect(screen.getByLabelText('ФИО')).toHaveValue('Дмитрий Волков')
    await user.type(screen.getByLabelText('E-mail'), 'volkov@')
    expect(screen.getByText(/Проверьте e-mail/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Сохранить изменения' })).toBeDisabled()
    await user.type(screen.getByLabelText('E-mail'), 'example.ru')
    // Второй телефон совпадает с основным телефоном Анны Михайловой.
    await user.type(screen.getByLabelText('Второй телефон'), '8 916 245-18-72')
    expect(screen.getByText(/Этот телефон уже есть в карточке: Анна Михайлова/)).toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: 'Постоянный администратор' }))
    await user.click(screen.getByRole('option', { name: 'Администратор песочницы' }))
    await user.click(screen.getByRole('button', { name: 'Сохранить изменения' }))
    expect(await screen.findByText('volkov@example.ru')).toBeInTheDocument()
    expect(screen.getByText('8 916 245-18-72')).toBeInTheDocument()
    expect(screen.getByText('Администратор песочницы', { selector: '.info-row *' })).toBeInTheDocument()
    // Основной телефон не изменился.
    expect(screen.getByText('+7 903 782-44-16')).toBeInTheDocument()
  })

  it('on the server sends the new fields; a disabled administrator stays with a note', async () => {
    let put: Record<string, unknown> | null = null
    const assigned = { id: '00000000-0000-0000-0000-0000000a0009', fullName: 'Бывший администратор', active: false }
    withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.includes('/patients/') && method === 'PUT') {
        put = JSON.parse(String(init?.body))
        return jsonResponse({ ...anna, fullName: 'Анна Михайлова', email: 'anna@example.ru', secondPhone: null, assignedStaff: assigned, contacts: anna.contacts })
      }
      if (address.includes('/patients')) return jsonResponse([{ ...anna, assignedStaff: assigned }])
      if (address.endsWith('/staff')) return jsonResponse([])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText('Бывший администратор · учётная запись отключена')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Редактировать' }))
    expect(screen.getByRole('combobox', { name: 'Постоянный администратор' })).toHaveTextContent('Бывший администратор · учётная запись отключена')
    await user.type(screen.getByLabelText('E-mail'), 'anna@example.ru')
    await user.click(screen.getByRole('button', { name: 'Сохранить изменения' }))
    await waitFor(() => expect(put).not.toBeNull())
    expect(put).toEqual({ fullName: 'Анна Михайлова', birthDate: '1991-05-14', phone: '+79160000000', secondPhone: '', email: 'anna@example.ru', assignedStaffId: assigned.id })
  })

  it('the administrator edits patient details', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openPage(user, 'Пациенты')
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
    await openPage(user, 'Пациенты')
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
    await openPage(user, 'Пациенты')
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
    await openPage(user, 'Пациенты')
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
    await openPage(user, 'Пациенты')
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
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText(/Роль «Менеджер» не имеет доступа/)).toBeInTheDocument()
  })

  it('hides clinical data from the administrator', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    expect(screen.getByText('Клинические данные недоступны')).toBeInTheDocument()
    expect(screen.getByText(/Роль «Администратор» не имеет доступа/)).toBeInTheDocument()
    expect(screen.queryByText(/Тревожно-депрессивная/)).not.toBeInTheDocument()
  })
})
