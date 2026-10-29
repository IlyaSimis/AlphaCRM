import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anna, enterDemo, goOffline, jsonResponse, openPage, renderApp, withAuth } from '../../test/helpers'

const dashboard = {
  role: 'ADMINISTRATOR', monthStart: '2026-10-01', today: '2026-10-20', generatedAt: '2026-10-20T06:00:00Z',
  widgets: [
    { code: 'appointments.month', scope: 'CLINIC', value: 12, secondary: 5, unit: 'count', items: [] },
    { code: 'salary.month', scope: 'OWN', value: 7500000, secondary: null, unit: 'money', items: ['MONTH'] },
    { code: 'delivery.failed', scope: 'CLINIC', value: 2, secondary: 1, unit: 'count', items: [] },
  ],
}

/** Сервер главной: личные заметки и задачи регистратуры. */
const server = (tasks: unknown[] = [
  { id: 't-1', patientId: anna.id, title: 'Перезвонить в лабораторию', dueDate: '2026-10-19', assigneeId: '00000000-0000-0000-0000-0000000a0001', status: 'OPEN' },
  { id: 't-2', patientId: null, title: 'Задача на сегодня', dueDate: '2026-10-20', assigneeId: '00000000-0000-0000-0000-0000000a0001', status: 'OPEN' },
]) => (address: string) => {
  if (address.includes('/patients')) return jsonResponse([anna])
  if (address.includes('/registry/tasks')) return jsonResponse(tasks)
  if (address.includes('/registry/waitlist')) return jsonResponse([])
  if (address.includes('/workspace/items')) return jsonResponse([{
    id: 'note-1', title: 'Идеи для клиники', type: 'Заметка', body: 'Собрать предложения команды', status: 'Заметки', color: 'blue', completed: false,
    dueAt: null, remindAt: null, patientId: null, appointmentId: null, tags: [], fields: [], checklist: [],
    createdAt: '2026-10-19T08:00:00Z', updatedAt: '2026-10-20T08:00:00Z', completedAt: null, archivedAt: null,
  }])
  if (address.includes('/appointments')) return jsonResponse([])
  return jsonResponse({}, 404)
}

describe('«Сегодня»', () => {
  it('the administrator sees only month metrics, notes and overdue tasks', async () => {
    const fetchMock = withAuth(server(), 'admin', { dashboard })
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '20 октября 2026, вторник', level: 4 })).toBeInTheDocument()
    expect(await screen.findByText('приёмы за месяц')).toBeInTheDocument()
    expect(screen.getByText('зарплата за месяц')).toBeInTheDocument()
    expect(screen.getByText('оклад по действующим условиям')).toBeInTheDocument()
    expect(screen.queryByText('ошибок доставки')).not.toBeInTheDocument()

    const notes = screen.getByRole('region', { name: 'Заметки' })
    expect(await within(notes).findByText('Идеи для клиники')).toBeInTheDocument()
    const overdue = screen.getByRole('region', { name: 'Просроченные задачи' })
    expect(await within(overdue).findByText(/Перезвонить в лабораторию/)).toBeInTheDocument()
    expect(within(overdue).queryByText(/Задача на сегодня/)).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Записи на сегодня и завтра' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Завершены и не оплачены' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Ошибки доставки' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/outbox'))).toBe(false)
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/charges'))).toBe(false)

    await user.click(within(overdue).getByRole('button', { name: 'Просроченная задача: Перезвонить в лабораторию' }))
    expect(await screen.findByText('ЗАДАЧИ НА СЕГОДНЯ')).toBeInTheDocument()
    await openPage(user, 'Сегодня')
    await user.click(await screen.findByRole('button', { name: 'Открыть заметку: Идеи для клиники' }))
    expect(await screen.findByRole('heading', { name: 'Моё пространство', level: 4 })).toBeInTheDocument()
  })

  it('a cashier sees notes and an empty overdue block without unrelated requests', async () => {
    const fetchMock = withAuth(server([]), 't.cashier')
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Заметки' })).toBeInTheDocument()
    expect(await screen.findByText('Просроченных задач нет')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/workspace/items'))).toBe(true))
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/outbox'))).toBe(false)
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/charges'))).toBe(false)
  })

  it('a manager sees notes and overdue tasks; a doctor has no such page', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'MANAGER')
    expect(screen.getByRole('region', { name: 'Заметки' })).toHaveTextContent('Идеи для клиники')
    const overdue = screen.getByRole('region', { name: 'Просроченные задачи' })
    expect(within(overdue).getByText(/Перезвонить/)).toBeInTheDocument()
    expect(within(overdue).queryByText(/Обновить согласие/)).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Записи на сегодня и завтра' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Завершены и не оплачены' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'DOCTOR')
    const menu = document.querySelector('.desktop-sidebar') as HTMLElement
    expect(within(menu).queryByRole('button', { name: 'Сегодня' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Просроченные задачи' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toBeInTheDocument()
  })
})

const sidebar = () => document.querySelector('.desktop-sidebar') as HTMLElement
const content = () => document.querySelector('.content') as HTMLElement

describe('«Сегодня» — домашняя страница', () => {
  it('services are tabs at the top, their sections are in the side menu; the AlphaCRM button returns home', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    const tabs = screen.getByRole('tablist', { name: 'Сервисы' })
    expect(within(tabs).getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['Дашборды', 'Управление', 'База данных пациентов', 'Склад', 'Финансы', 'Диск'])
    expect(within(tabs).getByRole('tab', { name: 'Дашборды' })).toHaveAttribute('aria-selected', 'true')
    const overview = within(sidebar()).getByRole('navigation', { name: 'Обзор' })
    expect(within(overview).getAllByRole('button').map((button) => button.textContent)).toEqual(['Сегодня', 'Моё пространство', 'Аналитика', 'Мои условия работы'])
    expect(within(sidebar()).queryByRole('navigation', { name: 'Управление' })).not.toBeInTheDocument()
    expect(within(overview).getByRole('button', { name: 'Сегодня' })).toHaveClass('active')
    await user.click(within(tabs).getByRole('tab', { name: 'Управление' }))
    expect(within(within(sidebar()).getByRole('navigation', { name: 'Управление' })).getAllByRole('button').map((button) => button.textContent)).toEqual(['Сотрудники', 'График врачей', 'Журнал аудита', 'Внешние выводы'])

    // Вкладка сервиса открывает его первый раздел; разделы другого сервиса из меню уходят.
    await user.click(within(tabs).getByRole('tab', { name: 'База данных пациентов' }))
    const patients = within(sidebar()).getByRole('navigation', { name: 'Работа с пациентами' })
    expect(within(patients).getAllByRole('button').map((button) => button.textContent)).toEqual(['Расписание', 'Пациенты', 'Регистратура'])
    expect(within(sidebar()).queryByRole('button', { name: 'Сегодня' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('День расписания')).toBeInTheDocument()
    await user.click(within(tabs).getByRole('tab', { name: 'Финансы' }))
    expect(within(sidebar()).getAllByRole('button').map((button) => button.textContent)).toContain('Услуги и цены')

    // Карточка пациента подсвечивает пункт «Пациенты»; вкладка возвращает на последнюю страницу сервиса.
    await openPage(user, 'Пациенты')
    await user.click(screen.getByText('Анна Михайлова'))
    expect(within(sidebar()).getByRole('button', { name: 'Пациенты' })).toHaveClass('active')
    await user.click(within(tabs).getByRole('tab', { name: 'Дашборды' }))
    await user.click(within(tabs).getByRole('tab', { name: 'База данных пациентов' }))
    expect(within(sidebar()).getByRole('button', { name: 'Пациенты' })).toHaveClass('active')

    await user.click(within(sidebar()).getByRole('button', { name: 'На главную' }))
    expect(screen.getByRole('heading', { name: '20 октября 2026, вторник', level: 4 })).toBeInTheDocument()
    expect(within(tabs).getByRole('tab', { name: 'Дашборды' })).toHaveAttribute('aria-selected', 'true')
    // Повторное нажатие на уже открытой «Сегодня» ничего не ломает.
    await user.click(within(sidebar()).getByRole('button', { name: 'На главную' }))
    expect(screen.getByRole('heading', { name: '20 октября 2026, вторник', level: 4 })).toBeInTheDocument()
    // «Сейчас» в тестах — 06:00 по часам клиники (UTC): утро, имя — первое слово ФИО.
    expect(within(content()).getByText('Доброе утро, Администратор')).toHaveClass('greeting--large')
    // В шапке страницы — только строка с датой, без заголовка «Сегодня»; подзаголовка нет.
    expect(within(document.querySelector('.topbar') as HTMLElement).queryByRole('heading', { name: 'Сегодня' })).not.toBeInTheDocument()
    expect(within(document.querySelector('.topbar') as HTMLElement).getByText(/РАБОЧИЙ ДЕНЬ/)).toBeInTheDocument()
    expect(screen.queryByText(/Что требует внимания/)).not.toBeInTheDocument()
    expect(within(content()).queryByText('Администратор песочницы')).not.toBeInTheDocument()
  })

  it('the doctor starts with the schedule; «Дашборды» hold only his own terms; the AlphaCRM button returns to the schedule and keeps open visits', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'DOCTOR')
    expect(screen.getByRole('tab', { name: 'База данных пациентов' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.queryByRole('tab', { name: 'Финансы' })).not.toBeInTheDocument()
    expect(within(sidebar()).queryByRole('button', { name: 'Сегодня' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Начать приём 09:00 Анна Михайлова' }))
    expect(await screen.findByLabelText('Диагноз')).toBeInTheDocument()
    await user.click(within(sidebar()).getByRole('button', { name: 'На главную' }))
    expect(screen.getByLabelText('День расписания')).toBeInTheDocument()
    expect(within(sidebar()).getByRole('navigation', { name: 'Открытые приёмы' })).toHaveTextContent('Приём: Анна Михайлова · 09:00')
    // Открытый приём виден и из другого сервиса.
    await user.click(screen.getByRole('tab', { name: 'Диск' }))
    expect(within(sidebar()).getByRole('navigation', { name: 'Открытые приёмы' })).toBeInTheDocument()
    await user.click(within(sidebar()).getByRole('button', { name: 'На главную' }))
    expect(screen.getByLabelText('День расписания')).toBeInTheDocument()
  })

  it('quick actions by role: administrator and manager — three, cashier — only the cashier, nurse — booking', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    const names = ['Новая запись', 'Новый пациент', 'Касса']
    const actions = () => within(content()).queryByRole('group', { name: 'Быстрые действия' })
    const visible = () => names.filter((name) => actions() && within(actions() as HTMLElement).queryByRole('button', { name }))
    await enterDemo(user, 'ADMINISTRATOR')
    expect(visible()).toEqual(names)
    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'CASHIER')
    expect(visible()).toEqual(['Касса'])
    await user.click(within(actions() as HTMLElement).getByRole('button', { name: 'Касса' }))
    expect(screen.getAllByText('Счёт не выставлен').length).toBeGreaterThan(0)
    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'MANAGER')
    expect(visible()).toEqual(names)
    await user.click(screen.getByRole('button', { name: 'Выйти' }))
    await enterDemo(user, 'NURSE')
    expect(visible()).toEqual(['Новая запись'])
  })

  it('«Новая запись» and «Новый пациент» open the same dialogs as the schedule and the patient list', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'ADMINISTRATOR')
    const actions = within(content()).getByRole('group', { name: 'Быстрые действия' })
    await user.click(within(actions).getByRole('button', { name: 'Новая запись' }))
    const booking = screen.getByRole('dialog')
    expect(booking).toHaveTextContent('Новая запись')
    expect(within(booking).getByLabelText('Дата')).toHaveValue('2026-10-20')
    expect(within(booking).getByRole('button', { name: /создать запись/i })).toBeInTheDocument()
    await user.click(within(booking).getByRole('button', { name: 'Отмена' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    await user.click(within(actions).getByRole('button', { name: 'Новый пациент' }))
    const patient = screen.getByRole('dialog')
    expect(within(patient).getByText('Новый пациент')).toBeInTheDocument()
    expect(within(patient).getByLabelText(/^Фамилия/)).toHaveValue('')
    await user.type(within(patient).getByLabelText(/^Фамилия/), 'Белова')
    await user.type(within(patient).getByLabelText(/^Имя/), 'Ирина')
    fireEvent.change(within(patient).getByLabelText(/^Дата рождения/), { target: { value: '1994-03-12' } })
    await user.type(within(patient).getByLabelText(/^Телефон\s*\*?$/), '+7 903 111-22-33')
    await user.click(within(patient).getByRole('button', { name: /сохранить пациента/i }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await openPage(user, 'Пациенты')
    expect(screen.getByText('Белова Ирина')).toBeInTheDocument()
  })
})
