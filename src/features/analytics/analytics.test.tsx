import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { chooseRole, enterDemo, goOffline, renderApp, openPage, menuHas } from '../../test/helpers'

describe('analytics', () => {
  it('a doctor sees neither analytics nor audit log; a manager sees the charts', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user)
    expect(await menuHas(user, 'Аналитика')).toBe(false)
    await chooseRole(user, 'MANAGER')
    await openPage(user, 'Аналитика')
    expect(screen.getByRole('heading', { name: 'Посещаемость и приёмы' })).toBeInTheDocument()
    expect(screen.getByText('посещаемость')).toBeInTheDocument()
    expect(screen.getByLabelText('20.10: всего 5')).toBeInTheDocument()
    expect(screen.getByText('Нагрузка по врачам')).toBeInTheDocument()
    await openPage(user, 'Журнал аудита')
    expect(screen.getByText(/доступен только при подключённом API/)).toBeInTheDocument()
  })
})
