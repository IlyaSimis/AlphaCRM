import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { chooseRole, goOffline, renderApp } from '../../test/helpers'

describe('аналитика', () => {
  it('врач не видит аналитику и журнал, менеджер видит графики', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    expect(screen.queryByRole('button', { name: 'Аналитика' })).not.toBeInTheDocument()
    await chooseRole(user, 'Менеджер')
    await user.click(screen.getByRole('button', { name: 'Аналитика' }))
    expect(screen.getByRole('heading', { name: 'Посещаемость и приёмы' })).toBeInTheDocument()
    expect(screen.getByText('посещаемость')).toBeInTheDocument()
    expect(screen.getByLabelText('20.10: всего 5')).toBeInTheDocument()
    expect(screen.getByText('Нагрузка по врачам')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Журнал аудита' }))
    expect(screen.getByText(/доступен только при подключённом API/)).toBeInTheDocument()
  })
})
