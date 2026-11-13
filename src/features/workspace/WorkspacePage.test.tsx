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

  it('opens the board by default, toggles checklist items in cards and saves a tag without Enter', async () => {
    localStorage.setItem('alphacrm.workspace.staff-test', JSON.stringify({ items: [{
      id: 'task-1', title: 'Составная задача', type: 'Задача', body: '', status: 'В работе', color: 'blue', completed: false,
      dueAt: null, remindAt: null, patientId: null, appointmentId: null, tags: ['срочно'], fields: [],
      checklist: [{ text: 'Подготовить документы', done: false }, { text: 'Проверить результат', done: true }],
      createdAt: '2026-10-20T08:00:00Z', updatedAt: '2026-10-20T08:00:00Z', completedAt: null, archivedAt: null,
    }], views: [] }))
    const user = userEvent.setup()
    render(<WorkspacePage apiOnline={false} selfId="staff-test" patients={[]} appointments={[]} notify={vi.fn()} />)

    const board = screen.getByRole('region', { name: 'Доска задач' })
    expect(screen.queryByRole('region', { name: 'Список задач' })).not.toBeInTheDocument()
    expect(within(board).getByText('Подготовить документы')).toBeInTheDocument()
    expect(within(board).getByText('Проверить результат')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Сохранить вид' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Сбросить' })).not.toBeInTheDocument()

    const checklistItem = within(board).getByRole('checkbox', { name: 'Подпункт: Подготовить документы' })
    expect(checklistItem).not.toBeChecked()
    await user.click(checklistItem)
    expect(checklistItem).toBeChecked()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await waitFor(() => {
      const stored = JSON.parse(localStorage.getItem('alphacrm.workspace.staff-test')!)
      expect(stored.items[0].checklist[0].done).toBe(true)
    })

    await user.click(screen.getByRole('button', { name: 'Список' }))
    const list = screen.getByRole('region', { name: 'Список задач' })
    expect(within(list).getByRole('list', { name: 'Пункты: Составная задача' })).toHaveTextContent('Подготовить документы')
    await user.click(screen.getByRole('button', { name: 'Доска' }))

    await user.click(screen.getByRole('button', { name: 'Составная задача' }))
    expect(screen.getByRole('dialog')).toHaveTextContent('Составная задача')
    expect(screen.queryByLabelText('Название')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Сохранить' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Редактировать' }))
    await user.type(screen.getByLabelText('Теги'), 'новый')
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await user.click(screen.getByRole('combobox', { name: 'Тег' }))
    expect(screen.getByRole('option', { name: 'срочно' })).toBeInTheDocument()
    await user.click(screen.getByRole('option', { name: 'новый' }))
    expect(screen.getByRole('button', { name: 'Составная задача' })).toBeInTheDocument()
  })

  it('assigns a task to another employee and shows it in both workspaces', async () => {
    const staff = [
      { id: 'staff-test', fullName: 'Илья Менеджер', role: 'MANAGER' as const },
      { id: 'staff-doctor', fullName: 'Мария Врач', role: 'DOCTOR' as const },
    ]
    const user = userEvent.setup()
    const view = render(<WorkspacePage apiOnline={false} selfId="staff-test" staff={staff} patients={[]} appointments={[]} notify={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: 'Создать' }))
    await user.type(screen.getByRole('textbox', { name: /^Название/ }), 'Подготовить общий отчёт')
    await user.click(screen.getByRole('combobox', { name: 'Исполнитель' }))
    await user.click(screen.getByRole('option', { name: 'Мария Врач · врач' }))
    await user.click(screen.getByRole('button', { name: 'Сохранить' }))

    expect(await screen.findByRole('button', { name: 'Подготовить общий отчёт' })).toBeInTheDocument()
    expect(screen.getByText('Исполнитель: Мария Врач')).toBeInTheDocument()
    const assignedStore = JSON.parse(localStorage.getItem('alphacrm.workspace.staff-doctor')!)
    expect(assignedStore.items[0]).toMatchObject({ title: 'Подготовить общий отчёт', creatorId: 'staff-test', assigneeId: 'staff-doctor' })

    view.unmount()
    const assigneeView = render(<WorkspacePage apiOnline={false} selfId="staff-doctor" staff={staff} patients={[]} appointments={[]} notify={vi.fn()} />)
    expect(await screen.findByRole('button', { name: 'Подготовить общий отчёт' })).toBeInTheDocument()
    expect(screen.getByText('Автор: Илья Менеджер')).toBeInTheDocument()
    await user.click(screen.getByRole('combobox', { name: 'Исполнитель' }))
    await user.click(screen.getByRole('option', { name: 'Назначенные мне' }))
    expect(screen.getByRole('button', { name: 'Подготовить общий отчёт' })).toBeInTheDocument()

    assigneeView.unmount()
    render(<WorkspacePage apiOnline={false} selfId="staff-test" staff={staff} patients={[]} appointments={[]} notify={vi.fn()} />)
    await user.click(screen.getByRole('combobox', { name: 'Исполнитель' }))
    await user.click(screen.getByRole('option', { name: 'Назначенные мне' }))
    expect(screen.getByRole('button', { name: 'Собрать план на неделю' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Подготовить общий отчёт' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Идеи для клиники' })).not.toBeInTheDocument()
  })
})
