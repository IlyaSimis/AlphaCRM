import { act, screen, waitFor } from '@testing-library/react'
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

  emit(message: StaffChatMessage) {
    this.onmessage?.({ data: JSON.stringify({ type: 'MESSAGE_CREATED', message }) } as MessageEvent)
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
  return { id, conversationId, senderId: own ? admin.id : manager.id, senderName: own ? admin.fullName : manager.fullName, body, createdAt: '2026-11-06T12:01:00Z', mentionIds: [], own, mentioned: false }
}

function chatApi(conversations: StaffConversation[], messages: StaffChatMessage[], initialPreferences: Partial<StaffChatPreferences> = {}) {
  const preferences: StaffChatPreferences = { notificationsEnabled: true, soundEnabled: true, browserEnabled: false, ...initialPreferences }
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
    if (address.includes('/messages') && method === 'GET') return jsonResponse(messages)
    if (address.includes('/messages') && method === 'POST') {
      const input = JSON.parse(String(init?.body))
      const conversationId = /conversations\/([^/]+)\/messages/.exec(address)?.[1] ?? 'chat-direct'
      const created = { ...message(messages.length + 1, conversationId, input.text, true), mentionIds: input.mentionIds }
      messages.push(created)
      return jsonResponse(created, 201)
    }
    if (address.includes('/read') && method === 'POST') return jsonResponse(null, 204)
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
    await openPage(user, 'Сообщения')
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

    expect(await screen.findByRole('button', { name: 'Сообщения, непрочитанных: 2' })).toBeInTheDocument()
    act(() => FakeWebSocket.instances[0].emit(message(19, existing.id, 'Моё сообщение', true)))
    expect(screen.queryByRole('button', { name: 'Закрыть уведомление' })).not.toBeInTheDocument()

    act(() => {
      existing.unreadCount = 3
      existing.lastMessage = incoming
      FakeWebSocket.instances[0].emit(incoming)
    })
    expect(await screen.findByRole('button', { name: 'Сообщения, непрочитанных: 3' })).toBeInTheDocument()
    expect(await screen.findByText('Тестовый менеджер · Дежурная смена')).toBeInTheDocument()
    expect(screen.getByText('Пациент уже пришёл')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Открыть' }))
    expect(await screen.findByLabelText('Текст сообщения')).toBeInTheDocument()
    expect((await screen.findAllByText('Пациент уже пришёл')).length).toBeGreaterThan(0)
    expect(await screen.findByRole('button', { name: 'Сообщения, непрочитанных: 0' })).toBeInTheDocument()
    await waitFor(() => expect(fetchMock.mock.calls.some(([url, init]) => String(url).includes('/chat-group/read') && init?.method === 'POST')).toBe(true))
  })

  it('creates a group with selected employees only', async () => {
    const conversations: StaffConversation[] = []
    const fetchMock = chatApi(conversations, [])
    vi.stubGlobal('WebSocket', FakeWebSocket)
    const user = userEvent.setup()
    renderApp()
    expect(await screen.findByText('API подключён')).toBeInTheDocument()
    await openPage(user, 'Сообщения')
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
    await openPage(user, 'Сообщения')
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
    await openPage(user, 'Сообщения')
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
    expect(await screen.findByRole('button', { name: 'Сообщения, непрочитанных: 2' })).toBeInTheDocument()
    await openPage(user, 'Сообщения')
    expect(await screen.findByText('Дежурная смена')).toBeInTheDocument()
    await waitFor(() => expect(FakeWebSocket.instances).toHaveLength(1))

    act(() => {
      existing.unreadCount = 3
      FakeWebSocket.instances[0].emit(message(32, existing.id, 'Сообщение без оповещения', false))
    })
    expect(await screen.findByRole('button', { name: 'Сообщения, непрочитанных: 3' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Закрыть уведомление' })).not.toBeInTheDocument()
    expect(FakeNotification.instances).toHaveLength(0)
    expect(audio).not.toHaveBeenCalled()
  })
})
