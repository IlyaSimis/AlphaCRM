import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { WorkspacePage } from './WorkspacePage'

describe('workspace page', () => {
  it('creates a quick task and persists it in the demo workspace', async () => {
    const user = userEvent.setup()
    render(<WorkspacePage apiOnline={false} selfId="staff-test" patients={[]} appointments={[]} notify={vi.fn()} />)
    await user.type(screen.getByLabelText('Быстрая задача'), 'Свободная задача пользователя')
    await user.click(screen.getByRole('button', { name: 'Добавить' }))
    expect(await screen.findByRole('button', { name: 'Свободная задача пользователя' })).toBeInTheDocument()
    expect(localStorage.getItem('alphacrm.workspace.staff-test')).toContain('Свободная задача пользователя')
  })
})
