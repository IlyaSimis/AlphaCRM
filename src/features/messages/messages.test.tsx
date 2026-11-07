import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StaffChatMessage, StaffChatPreferences, StaffConversation } from '../../api/staffChat'
import { jsonResponse, openPage, renderApp, testAccounts, withAuth } from '../../test/helpers'

class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  readonly url: string
  onopen: ((event: Event) => void) | null = null
  onmessage: ((event: MessageEvent) => void) | null = null
  onclose: ((event: CloseEvent) => void) | null = null
  onerror: ((event: Event) => void) | null = null

  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }

  close() {}

  emit(message: StaffChatMessage, type: 'MESSAGE_CREATED' | 'MESSAGE_UPDATED' = 'MESSAGE_CREATED') {
    this.onmessage?.({ data: JSON.stringify({ type, message }) } as MessageEvent)
  }
}

class FakeNotification {
  static instances: FakeNotification[] = []
  static permission: NotificationPermission = 'granted'
  static requestPermission = vi.fn(async () => {
    FakeNotification.permission = 'granted'
    return FakeNotification.permission
  })
  readonly title: string
  readonly options: NotificationOptions
  onclick: (() => void) | null = null
  closed = false

  constructor(title: string, options: NotificationOptions) {
    this.title = title
    this.options = options
    FakeNotification.instances.push(this)
  }

  close() {
    this.closed = true
  }
}

const accounts = testAccounts()
const admin = accounts.admin
const manager = accounts['t.manager']
const cashier = accounts['t.cashier']
const colleagues = [
  { id: manager.id, fullName: manager.fullName, role: manager.role },
  { id: cashier.id, fullName: cashier.fullName, role: cashier.role },
]

function conversation(id: string, kind: 'DIRECT' | 'GROUP', title: string): StaffConversation {
  return {
    id, kind, title,
    members: [
      { id: admin.id, fullName: admin.fullName, role: admin.role, active: true },
      { id: manager.id, fullName: manager.fullName, role: manager.role, active: true },
      { id: cashier.id, fullName: cashier.fullName, role: cashier.role, active: true },
    ],
    lastMessage: null, unreadCount: 0, updatedAt: '2026-11-06T12:00:00Z',
  }
}

function message(id: number, conversationId: string, body: string, own: boolean): StaffChatMessage {
  return {
    id, conversationId, senderId: own ? admin.id : manager.id,
    senderName: own ? admin.fullName : manager.fullName, body,
    createdAt: '2026-11-06T12:01:00Z', mentionIds: [], own, mentioned: false,
    readByAll: false, editedAt: null, deleted: false, attachments: [], reactions: [],
  }
}

function chatApi(conversations: StaffConversation[], messages: StaffChatMessage[], initialPreferences: Partial<StaffChatPreferences> = {}, older: StaffChatMessage[] = []) {
  const preferences: StaffChatPreferences = { notificationsEnabled: true, soundEnabled: true, browserEnabled: false, ...initialPreferences }
  const allMessages = () => [...older, ...messages]
  return withAuth((address, init) => {
    const method = init?.method ?? 'GET'
    if (address.endsWith('/staff-chat/colleagues')) return jsonResponse(colleagues)
    if (address.endsWith('/staff-chat/preferences') && method === 'GET') return jsonResponse(preferences)
    if (address.endsWith('/staff-chat/preferences') && method === 'PUT') {
      Object.assign(preferences, JSON.parse(String(init?.body)))
      return jsonResponse(preferences)
    }
    if (address.endsWith('/staff-chat/conversations') && method === 'GET') return jsonResponse(conversations.map((item) => ({ ...item })))
    if (address.endsWith('/staff-chat/conversations/direct') && method === 'POST') {
      const input = JSON.parse(String(init?.body))
      const created = conversation('chat-direct', 'DIRECT', input.staffId === manager.id ? manager.fullName : cashier.fullName)
      conversations.push(created)
      return jsonResponse(created)
    }
    if (address.endsWith('/staff-chat/conversations/group') && method === 'POST') {
      const input = JSON.parse(String(init?.body))
      const created = conversation('chat-group', 'GROUP', input.title)
      conversations.push(created)
      return jsonResponse(created, 201)
    }
    if (address.includes('/messages') && method === 'GET') return jsonResponse(address.includes('beforeId=') ? older : messages)
    if (address.includes('/messages') && method === 'POST') {
      const form = init?.body instanceof FormData ? init.body : null
      const input = form ? null : JSON.parse(String(init?.body))
      const text = form ? String(form.get('text') ?? '') : input.text
      const mentionIds = form ? form.getAll('mentionIds').map(String) : input.mentionIds
      const conversationId = /conversations\/([^/]+)\/messages/.exec(address)?.[1] ?? 'chat-direct'
      const uploaded = form?.getAll('files').filter((item): item is File => item instanceof File) ?? []
      const attachments = uploaded.map((file, index) => ({ id: `attachment-${index}`, name: file.name, contentType: file.type, sizeBytes: file.size, downloadUrl: `/api/v1/staff-chat/attachments/attachment-${index}` }))
      const created = { ...message(Math.max(0, ...allMessages().map((item) => item.id)) + 1, conversationId, text, true), mentionIds, attachments }
      messages.push(created)
      return jsonResponse(created, 201)
    }
    if (address.includes('/read') && method === 'POST') {
      const input = JSON.parse(String(init?.body))
      const conversationId = /conversations\/([^/]+)\/read/.exec(address)?.[1] ?? ''
      const target = allMessages().find((item) => item.id === input.messageId) ?? message(input.messageId, conversationId, '', false)
      const read = { ...target, readByAll: target.own ? target.readByAll : true }
      conversations.forEach((item) => { if (item.id === conversationId) item.unreadCount = 0 })
      return jsonResponse(read)
    }
    const actionMatch = /\/staff-chat\/messages\/(\d+)(\/reaction)?$/.exec(address)
    if (actionMatch) {
      const target = allMessages().find((item) => item.id === Number(actionMatch[1]))
      if (!target) return jsonResponse({}, 404)
      if (method === 'PATCH') {
        target.body = JSON.parse(String(init?.body)).text
        target.editedAt = '2026-11-06T12:02:00Z'
      } else if (method === 'DELETE') {
        Object.assign(target, { body: '', deleted: true, editedAt: null, attachments: [], reactions: [] })
      } else if (method === 'PUT' && actionMatch[2]) {
        const emoji = JSON.parse(String(init?.body)).emoji
        const current = target.reactions.find((item) => item.emoji === emoji)
        target.reactions = current ? [] : [{ emoji, count: 1, own: true }]
      }
      return jsonResponse(target)
    }
    if (address.includes('/patients')) return jsonResponse([])
    if (address.includes('/appointments')) return jsonResponse([])
    return jsonResponse({}, 404)
  })
}

describe('staff-only messenger', () => {
  beforeEach(() => {
    FakeWebSocket.instances = []
  })

  it('creates a direct chat, sends a message and receives a staff WebSocket event', async () => {
    const conversations: StaffConversation[] = []
    const messages: StaffChatMessage[] = []
    const fetchMock = chatApi(conversations, messages)
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: 'Личный чат' }))
    await user.click(screen.getByRole('button', { name: /Тестовый менеджер/ }))
    const input = await screen.findByLabelText('Текст сообщения')
    await user.type(input, 'Закрываем смену')
    expect(input).toHaveValue('Закрываем смену')
    const submit = screen.getByRole('button', { name: 'Отправить сообщение' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)
    expect(await screen.findByText('Закрываем смену')).toBeInTheDocument()
    const direct = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/conversations/direct'))
    expect(JSON.parse(String(direct?.[1]?.body))).toEqual({ staffId: manager.id })

    act(() => FakeWebSocket.instances[0].emit(message(20, 'chat-direct', 'Да, всё готово', false)))
    expect(await screen.findByText('Да, всё готово')).toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes('/chat-direct/read') && init?.method === 'POST')).toBe(true))
  })

  it('shows global unread count and opens an incoming message from another CRM section', async () => {
    const existing = conversation('chat-group', 'GROUP', 'Дежурная смена')
    existing.unreadCount = 2
    const incoming = message(20, existing.id, 'Пациент уже пришёл', false)
    const conversations = [existing]
    const fetchMock = chatApi(conversations, [incoming])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()

    expect(await screen.findByRole('tab', { name: 'StaffChat, непрочитанных: 2' })).toBeInTheDocument()
    act(() => FakeWebSocket.instances[0].emit(message(19, existing.id, 'Моё сообщение', true)))
    expect(screen.queryByRole('button', { name: 'Закрыть уведомление' })).not.toBeInTheDocument()

    act(() => {
      existing.unreadCount = 3
      existing.lastMessage = incoming
      FakeWebSocket.instances[0].emit(incoming)
    })
    expect(await screen.findByRole('tab', { name: 'StaffChat, непрочитанных: 3' })).toBeInTheDocument()
    expect(await screen.findByText('Тестовый менеджер · Дежурная смена')).toBeInTheDocument()
    expect(screen.getByText('Пациент уже пришёл')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Открыть' }))
    expect(await screen.findByLabelText('Текст сообщения')).toBeInTheDocument()
    expect((await screen.findAllByText('Пациент уже пришёл')).length).toBeGreaterThan(0)
    expect(await screen.findByRole('tab', { name: 'StaffChat, непрочитанных: 0' })).toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes('/chat-group/read') && init?.method === 'POST')).toBe(true))
  })

  it('creates a group with selected employees only', async () => {
    const conversations: StaffConversation[] = []
    const fetchMock = chatApi(conversations, [])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: 'Создать группу' }))
    await user.type(screen.getByLabelText('Название группы'), 'Дежурная смена')
    await user.click(screen.getByRole('checkbox', { name: /Тестовый менеджер/ }))
    await user.click(screen.getByRole('checkbox', { name: /Тестовый кассир/ }))
    await user.click(screen.getByRole('button', { name: 'Создать' }))
    expect(await screen.findByText('Участников: 3')).toBeInTheDocument()
    const group = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/conversations/group'))
    expect(JSON.parse(String(group?.[1]?.body))).toEqual({ title: 'Дежурная смена', memberIds: [manager.id, cashier.id] })
  })

  it('selects an @mention and sends the member id with a visible incoming mention', async () => {
    const existing = conversation('chat-group', 'GROUP', 'Дежурная смена')
    const fetchMock = chatApi([existing], [])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: /Дежурная смена/ }))
    const input = await screen.findByLabelText('Текст сообщения')
    await user.type(input, '@Тест')
    await user.click(await screen.findByRole('button', { name: /Упомянуть Тестовый менеджер/ }))
    await waitFor(() => expect(input).toHaveValue('@Тестовый менеджер '))
    await user.type(input, 'проверьте запись')
    await user.click(screen.getByRole('button', { name: 'Отправить сообщение' }))
    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes('/messages') && init?.method === 'POST')).toBe(true))
    const sent = fetchMock.mock.calls.find(([url, init]) => String(url).includes('/messages') && init?.method === 'POST')
    expect(JSON.parse(String(sent?.[1]?.body))).toEqual({ text: '@Тестовый менеджер проверьте запись', mentionIds: [manager.id] })

    const incoming = { ...message(30, existing.id, '@Администратор, проверьте запись', false), mentionIds: [admin.id], mentioned: true }
    act(() => FakeWebSocket.instances[0].emit(incoming))
    expect(await screen.findByText('Вас упомянули')).toBeInTheDocument()
  })

  it('saves personal switches and requests browser permission', async () => {
    const existing = conversation('chat-group', 'GROUP', 'Дежурная смена')
    const fetchMock = chatApi([existing], [])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    FakeNotification.permission = 'default'
    FakeNotification.instances = []
    vi.stubGlobal('Notification', FakeNotification)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: 'Настройки уведомлений' }))
    await user.click(screen.getByRole('switch', { name: 'Уведомления о новых сообщениях' }))
    await user.click(screen.getByRole('switch', { name: 'Звуковой сигнал' }))
    await user.click(screen.getByRole('switch', { name: 'Уведомления браузера' }))
    await waitFor(() => expect(FakeNotification.requestPermission).toHaveBeenCalled())
    await waitFor(() => {
      const writes = fetchMock.mock.calls.filter(([url, init]) => String(url).endsWith('/staff-chat/preferences') && init?.method === 'PUT')
      expect(writes.at(-1)?.[1]?.body).toBe(JSON.stringify({ notificationsEnabled: false, soundEnabled: false, browserEnabled: true }))
    })
    expect(await screen.findByText('Отключение не влияет на счётчик непрочитанных сообщений.')).toBeInTheDocument()
  })

  it('creates a browser notification for a hidden tab and opens its conversation on click', async () => {
    const existing = conversation('chat-group', 'GROUP', 'Дежурная смена')
    chatApi([existing], [], { soundEnabled: false, browserEnabled: true })
    vi.stubGlobal('WebSocket', FakeWebSocket)
    FakeNotification.permission = 'granted'
    FakeNotification.instances = []
    vi.stubGlobal('Notification', FakeNotification)
    vi.spyOn(document, 'hasFocus').mockReturnValue(false)
    vi.spyOn(window, 'focus').mockImplementation(() => undefined)
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1))
    await act(async () => { await Promise.resolve() })

    act(() => FakeWebSocket.instances[0].emit(message(31, existing.id, 'Нужна помощь у стойки', false)))
    await waitFor(() => expect(FakeNotification.instances).toHaveLength(1))
    const notification = FakeNotification.instances[0]
    expect(notification.title).toBe('Дежурная смена')
    expect(notification.options.body).toBe('Тестовый менеджер: Нужна помощь у стойки')
    act(() => notification.onclick?.())
    expect(await screen.findByLabelText('Текст сообщения')).toBeInTheDocument()
    expect(notification.closed).toBe(true)
  })

  it('keeps unread counts but suppresses every alert when notifications are disabled', async () => {
    const existing = conversation('chat-group', 'GROUP', 'Дежурная смена')
    existing.unreadCount = 2
    chatApi([existing], [], { notificationsEnabled: false, soundEnabled: true, browserEnabled: true })
    vi.stubGlobal('WebSocket', FakeWebSocket)
    FakeNotification.permission = 'granted'
    FakeNotification.instances = []
    vi.stubGlobal('Notification', FakeNotification)
    const audio = vi.fn()
    vi.stubGlobal('AudioContext', audio)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByRole('tab', { name: 'StaffChat, непрочитанных: 2' })).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    expect(await screen.findByText('Дежурная смена')).toBeInTheDocument()
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1))

    act(() => {
      existing.unreadCount = 3
      FakeWebSocket.instances[0].emit(message(32, existing.id, 'Сообщение без оповещения', false))
    })
    expect(await screen.findByRole('tab', { name: 'StaffChat, непрочитанных: 3' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Закрыть уведомление' })).not.toBeInTheDocument()
    expect(FakeNotification.instances).toHaveLength(0)
    expect(audio).not.toHaveBeenCalled()
  })

  it('updates delivered and read ticks from a WebSocket event without creating an alert', async () => {
    const existing = conversation('chat-read', 'DIRECT', manager.fullName)
    const own = message(40, existing.id, 'Проверка статуса', true)
    chatApi([existing], [own])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: new RegExp(manager.fullName) }))
    expect(await screen.findByLabelText('Доставлено на сервер')).toBeInTheDocument()

    act(() => FakeWebSocket.instances[0].emit({ ...own, readByAll: true }, 'MESSAGE_UPDATED'))
    expect(await screen.findByLabelText('Прочитано всеми участниками')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Закрыть уведомление' })).not.toBeInTheDocument()
  })

  it('edits and softly deletes only through the author actions', async () => {
    const existing = conversation('chat-actions', 'DIRECT', manager.fullName)
    chatApi([existing], [message(41, existing.id, 'Первоначальный текст', true)])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: new RegExp(manager.fullName) }))
    await user.click(await screen.findByRole('button', { name: 'Редактировать сообщение 41' }))
    const editDialog = screen.getByRole('dialog')
    const editInput = within(editDialog).getByLabelText('Текст сообщения')
    await user.clear(editInput)
    await user.type(editInput, 'Исправленный текст')
    await user.click(within(editDialog).getByRole('button', { name: 'Сохранить' }))
    expect(await screen.findByText('Исправленный текст')).toBeInTheDocument()
    expect(screen.getByText('изменено')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Удалить сообщение 41' }))
    const deleteDialog = screen.getByRole('dialog')
    await user.click(within(deleteDialog).getByRole('button', { name: 'Удалить' }))
    expect(await screen.findByText('Сообщение удалено')).toBeInTheDocument()
    expect(screen.queryByText('Исправленный текст')).not.toBeInTheDocument()
  })

  it('adds and removes a reaction', async () => {
    const existing = conversation('chat-reactions', 'GROUP', 'Реакции')
    chatApi([existing], [message(42, existing.id, 'Оцените сообщение', false)])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: /Реакции/ }))
    await user.click(await screen.findByRole('button', { name: 'Реакции к сообщению 42' }))
    await user.click(screen.getByRole('button', { name: 'Поставить реакцию 👍' }))
    const reaction = await screen.findByRole('button', { name: 'Реакция 👍, 1' })
    await user.click(reaction)
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Реакция 👍, 1' })).not.toBeInTheDocument())
  })

  it('sends multipart attachments and rejects a file over 10 MB', async () => {
    const existing = conversation('chat-files', 'DIRECT', manager.fullName)
    const fetchMock = chatApi([existing], [])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: new RegExp(manager.fullName) }))
    await user.upload(screen.getByLabelText('Файлы сообщения'), new File(['data'], 'report.txt', { type: 'text/plain' }))
    await user.click(screen.getByRole('button', { name: 'Отправить сообщение' }))
    expect(await screen.findByRole('link', { name: 'report.txt · 4 Б' })).toHaveAttribute('download', 'report.txt')
    const sent = fetchMock.mock.calls.find(([url, init]) => String(url).includes('/messages') && init?.method === 'POST' && init.body instanceof FormData)
    const form = sent?.[1]?.body as FormData
    expect(form.get('text')).toBe('')
    expect((form.get('files') as File).name).toBe('report.txt')

    await user.upload(screen.getByLabelText('Файлы сообщения'), new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'large.bin'))
    expect(await screen.findByText('Каждый файл должен быть от 1 байта до 10 МБ')).toBeInTheDocument()
  })

  it('loads older messages before the first id and preserves the scroll position', async () => {
    const existing = conversation('chat-history', 'DIRECT', manager.fullName)
    const recent = Array.from({ length: 200 }, (_item, index) => message(index + 3, existing.id, `Новое ${index + 3}`, false))
    const older = [message(1, existing.id, 'Старое 1', false), message(2, existing.id, 'Старое 2', false)]
    const fetchMock = chatApi([existing], recent, {}, older)
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'StaffChat')
    await user.click(await screen.findByRole('button', { name: new RegExp(manager.fullName) }))
    expect(await screen.findByText('Новое 202')).toBeInTheDocument()
    const history = screen.getByLabelText('История сообщений')
    Object.defineProperty(history, 'scrollHeight', { configurable: true, value: 1000, writable: true })
    history.scrollTop = 0
    fireEvent.scroll(history)
    Object.defineProperty(history, 'scrollHeight', { configurable: true, value: 1400, writable: true })

    expect(await screen.findByText('Старое 1')).toBeInTheDocument()
    expect(history.scrollTop).toBe(400)
    expect(screen.queryByRole('button', { name: 'Загрузить предыдущие сообщения' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('beforeId=3'))).toBe(true)
  })
})
