import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PETROV_ID } from '../../api/client'
import { anna, enterDemo, goOffline, jsonResponse, openPage, renderApp, withAuth } from '../../test/helpers'

const appointment = (id: string, startsAt: string, status: string, extra: Record<string, unknown> = {}) => ({
  id, patientId: anna.id, practitionerId: PETROV_ID, startsAt, endsAt: startsAt.replace(':00:00Z', ':30:00Z'), serviceName: 'Повторный приём', status, ...extra,
})

/** Сервер «Сегодня»: записи на сегодня и завтра, задачи до сегодня, завершённые без счёта, ошибка доставки. */
const server = (tasks: unknown[] = [
  { id: 't-1', patientId: anna.id, title: 'Перезвонить в лабораторию', dueDate: '2026-10-19', assigneeId: '00000000-0000-0000-0000-0000000a0001', status: 'OPEN' },
  { id: 't-2', patientId: null, title: 'Задача на сегодня', dueDate: '2026-10-20', assigneeId: '00000000-0000-0000-0000-0000000a0001', status: 'OPEN' },
]) => (address: string) => {
  const url = new URL(address, 'http://localhost')
  if (address.includes('/patients')) return jsonResponse([anna])
  if (address.includes('/registry/tasks')) return jsonResponse(tasks)
  if (address.includes('/registry/waitlist')) return jsonResponse([])
  if (address.includes('/charges')) return jsonResponse([])
  if (url.pathname.endsWith('/outbox')) return jsonResponse([{
    id: 'o-1', topic: 'NOTIFICATION', template: 'appointment-reminder', aggregateType: 'APPOINTMENT', aggregateId: 'a-1', appointmentStartsAt: '2026-10-21T09:00:00Z',
    channel: 'WEBHOOK', status: 'FAILED', reason: 'HTTP_500', attempts: 5, createdAt: '2026-10-19T08:00:00Z', lastAttemptAt: '2026-10-19T09:15:00Z', nextAttemptAt: null, sentAt: null,
  }])
  if (address.includes('/appointments')) {
    if (url.searchParams.get('status') === 'COMPLETED') return jsonResponse([appointment('a-done', '2026-10-15T09:00:00Z', 'COMPLETED', { amountMinor: 300000, currency: 'RUB' })])
    if (url.searchParams.get('from') === '2026-10-20') return jsonResponse([
      appointment('a-1', '2026-10-21T09:00:00Z', 'CONFIRMED'),
      appointment('a-2', '2026-10-20T11:00:00Z', 'CANCELLED'),
    ])
    return jsonResponse([])
  }
  return jsonResponse({}, 404)
}

describe('«Сегодня»', () => {
  it('the administrator starts here and sees all four blocks; overdue skips today; a click opens the section', async () => {
    const fetchMock = withAuth(server(), 'admin')
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '20 октября 2026, вторник', level: 4 })).toBeInTheDocument()

    const upcoming = screen.getByRole('region', { name: 'Записи на сегодня и завтра' })
    expect(await within(upcoming).findByText('завтра 09:00')).toBeInTheDocument()
    expect(within(upcoming).getByText('Анна Михайлова')).toBeInTheDocument()
    expect(within(upcoming).getByText('Подтверждён')).toBeInTheDocument()
    expect(within(upcoming).queryByText('Отменена')).not.toBeInTheDocument()

    const overdue = screen.getByRole('region', { name: 'Просроченные задачи' })
    expect(await within(overdue).findByText(/Перезвонить в лабораторию/)).toBeInTheDocument()
    expect(within(overdue).queryByText(/Задача на сегодня/)).not.toBeInTheDocument()

    const unpaid = screen.getByRole('region', { name: 'Завершены и не оплачены' })
    expect(await within(unpaid).findByText(/3\s000,00/)).toBeInTheDocument()
    expect(within(unpaid).getByText('15.10 · Счёт не выставлен')).toBeInTheDocument()

    const failed = screen.getByRole('region', { name: 'Ошибки доставки' })
    expect(await within(failed).findByText('Напоминание за сутки · Вебхук клиники')).toBeInTheDocument()
    expect(within(failed).getByText('получатель ответил 500')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/outbox?status=FAILED'))).toBe(true)

    await user.click(within(overdue).getByRole('button', { name: 'Просроченная задача: Перезвонить в лабораторию' }))
    expect(await screen.findByText('ЗАДАЧИ НА СЕГОДНЯ')).toBeInTheDocument()
    await openPage(user, 'Сегодня')
    await user.click(await screen.findByRole('button', { name: /^Открыть карточку: Анна Михайлова/ }))
    expect(await screen.findByRole('heading', { name: 'Анна Михайлова' })).toBeInTheDocument()
  })

  it('a cashier sees no delivery errors and does not ask for them; an empty block says so', async () => {
    const fetchMock = withAuth(server([]), 't.cashier')
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Завершены и не оплачены' })).toBeInTheDocument()
    expect(await screen.findByText('Просроченных задач нет')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Ошибки доставки' })).not.toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/charges'))).toBe(true))
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/outbox'))).toBe(false)
  })

  it('a manager sees appointments, tasks and unpaid visits, but not deliveries; a doctor has no such page', async () => {
    goOffline()
    const user = userEvent.setup()
    renderApp()
    await enterDemo(user, 'MANAGER')
    expect(screen.getByRole('region', { name: 'Записи на сегодня и завтра' })).toBeInTheDocument()
    // В песочнице задача «Перезвонить» просрочена со вчера, «Обновить согласие» — на сегодня.
    const overdue = screen.getByRole('region', { name: 'Просроченные задачи' })
    expect(within(overdue).getByText(/Перезвонить/)).toBeInTheDocument()
    expect(within(overdue).queryByText(/Обновить согласие/)).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Завершены и не оплачены' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Ошибки доставки' })).not.toBeInTheDocument()

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
    expect(within(tabs).getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['Дашборды', 'База данных пациентов', 'Склад', 'Финансы', 'Диск'])
    expect(within(tabs).getByRole('tab', { name: 'Дашборды' })).toHaveAttribute('aria-selected', 'true')
    const overview = within(sidebar()).getByRole('navigation', { name: 'Обзор' })
    expect(within(overview).getAllByRole('button').map((button) => button.textContent)).toEqual(['Сегодня', 'Моё пространство', 'Аналитика', 'Мои условия работы'])
    expect(within(within(sidebar()).getByRole('navigation', { name: 'Управление' })).getAllByRole('button').map((button) => button.textContent)).toEqual(['Сотрудники', 'Журнал аудита', 'Внешние выводы'])
    expect(within(overview).getByRole('button', { name: 'Сегодня' })).toHaveClass('active')

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
    expect(screen.getByLabelText('Диагноз')).toBeInTheDocument()
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
    expect(within(patient).getByLabelText('ФИО')).toHaveValue('')
    await user.type(within(patient).getByLabelText('ФИО'), 'Ирина Белова')
    fireEvent.change(within(patient).getByLabelText('Дата рождения'), { target: { value: '1994-03-12' } })
    await user.type(within(patient).getByLabelText('Телефон'), '+7 903 111-22-33')
    await user.click(within(patient).getByRole('button', { name: /сохранить пациента/i }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    await openPage(user, 'Пациенты')
    expect(screen.getByText('Ирина Белова')).toBeInTheDocument()
  })
})
