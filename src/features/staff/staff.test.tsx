import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PETROV_ID } from '../../api/client'
import type { StaffItem } from '../../api/types'
import { anna, enterDemo, goOffline, jsonResponse, renderApp, signIn, testAccounts, withAuth } from '../../test/helpers'

describe('staff', () => {
  it('the sandbox has no people, only per-role accounts; you cannot deactivate yourself', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    await user.click(screen.getByRole('button', { name: 'Сотрудники' }))
    expect(screen.getByText('Администратор песочницы (вы)')).toBeInTheDocument()
    expect(screen.queryByText(/Смирнова|Кузнецов|Орлова/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Отключить Администратор песочницы' })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: 'Отключить Кассир песочницы' }))
    expect(await screen.findByRole('button', { name: 'Включить Кассир песочницы' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Новый сотрудник' }))
    await user.type(screen.getByLabelText('ФИО'), 'Ирина Белова')
    await user.type(screen.getByLabelText('Логин'), 'belova')
    await user.type(screen.getByLabelText('Временный пароль'), 'short')
    await user.click(screen.getByRole('button', { name: 'Создать' }))
    expect(screen.getByText('Пароль должен быть не короче 8 символов')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Временный пароль'), '-enough')
    await user.click(screen.getByRole('button', { name: 'Создать' }))
    expect(await screen.findByText('belova · Кассир')).toBeInTheDocument()
    expect(screen.getByText('Сменит пароль при входе')).toBeInTheDocument()
  })

  it('admin creates a doctor with a temporary password shown once; the doctor changes it right away', async () => {
    const accounts = testAccounts()
    let created: (StaffItem & { password: string }) | null = null
    const fetchMock = withAuth((address, init) => {
      const method = init?.method ?? 'GET'
      if (address.endsWith('/staff') && method === 'POST') {
        const body = JSON.parse(String(init?.body))
        created = { id: '00000000-0000-0000-0000-0000000a0099', fullName: body.fullName, username: body.username, role: body.role, practitionerId: body.practitionerId, active: true, mustChangePassword: true, password: body.password }
        const { password: _password, ...view } = created
        return jsonResponse(view, 201)
      }
      if (address.endsWith('/staff')) {
        const list = [accounts.admin]
        if (created) {
          const { password: _password, ...view } = created
          list.push(view)
        }
        return jsonResponse(list)
      }
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Сотрудники' }))
    expect(await screen.findByText('Администратор (вы)')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Новый сотрудник' }))
    await user.type(screen.getByLabelText('ФИО'), 'Врач Новый')
    await user.type(screen.getByLabelText('Логин'), 'new.doctor')
    await user.type(screen.getByLabelText('Временный пароль'), 'temporary-1')
    await user.click(screen.getByRole('combobox', { name: 'Роль' }))
    await user.click(screen.getByRole('option', { name: 'Врач' }))
    await user.click(screen.getByRole('combobox', { name: 'Карточка врача' }))
    await user.click(screen.getByRole('option', { name: /Александр Петров/ }))
    await user.click(screen.getByRole('button', { name: 'Создать' }))

    expect(await screen.findByText(/Он показан один раз/)).toBeInTheDocument()
    expect(screen.getByText('temporary-1')).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/staff') && init?.method === 'POST')
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({ fullName: 'Врач Новый', username: 'new.doctor', password: 'temporary-1', role: 'DOCTOR', practitionerId: PETROV_ID })
    expect(await screen.findByText('Сменит пароль при входе')).toBeInTheDocument()
  })

  it('with a temporary password only the password change is open', async () => {
    const fetchMock = withAuth((address) => {
      if (address.includes('/patients')) return jsonResponse([anna])
      if (address.includes('/appointments')) return jsonResponse([])
      return jsonResponse({}, 404)
    }, null, { mustChange: ['t.cashier'] })
    const user = userEvent.setup()
    renderApp()
    await signIn(user, 't.cashier')
    expect(await screen.findByText(/Вы вошли с временным паролем/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Расписание' })).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Временный пароль'), 'test-password-1')
    await user.type(screen.getByLabelText('Новый пароль'), 'test-password-1')
    await user.type(screen.getByLabelText('Повторите пароль'), 'test-password-1')
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))
    expect(screen.getByText('Новый пароль должен отличаться от временного')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Новый пароль'))
    await user.type(screen.getByLabelText('Новый пароль'), 'my-own-pass-1')
    await user.clear(screen.getByLabelText('Повторите пароль'))
    await user.type(screen.getByLabelText('Повторите пароль'), 'my-own-pass-1')
    await user.click(screen.getByRole('button', { name: 'Сменить пароль' }))
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Касса' })).toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/password'))).toBe(true))
  })
})
