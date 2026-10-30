import { render, screen, waitFor, within } from '@testing-library/react'
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

  it('shows checklist items in both layouts and puts saved tags into the filter without saved views', async () => {
    localStorage.setItem('alphacrm.workspace.staff-test', JSON.stringify({ items: [{
      id: 'task-1', title: 'Составная задача', type: 'Задача', body: '', status: 'В работе', color: 'blue', completed: false,
      dueAt: null, remindAt: null, patientId: null, appointmentId: null, tags: ['срочно'], fields: [],
      checklist: [{ text: 'Подготовить документы', done: false }, { text: 'Проверить результат', done: true }],
      createdAt: '2026-10-20T08:00:00Z', updatedAt: '2026-10-20T08:00:00Z', completedAt: null, archivedAt: null,
    }], views: [] }))
    const user = userEvent.setup()
    render(<WorkspacePage apiOnline={false} selfId="staff-test" patients={[]} appointments={[]} notify={vi.fn()} />)

    const list = screen.getByRole('region', { name: 'Список задач' })
    expect(within(list).getByRole('list', { name: 'Пункты: Составная задача' })).toHaveTextContent('Подготовить документы')
    await user.click(screen.getByRole('button', { name: 'Доска' }))
    const board = screen.getByRole('region', { name: 'Доска задач' })
    expect(within(board).getByText('Подготовить документы')).toBeInTheDocument()
    expect(within(board).getByText('Проверить результат')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Сохранить вид' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Сбросить' })).not.toBeInTheDocument()

    await user.click(within(board).getByRole('button', { name: 'Составная задача' }))
    await user.type(screen.getByLabelText('Теги'), 'новый{enter}')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await user.click(screen.getByRole('combobox', { name: 'Тег' }))
    expect(screen.getByRole('option', { name: 'срочно' })).toBeInTheDocument()
    await user.click(screen.getByRole('option', { name: 'новый' }))
    expect(within(board).getByRole('button', { name: 'Составная задача' })).toBeInTheDocument()
  })
})
