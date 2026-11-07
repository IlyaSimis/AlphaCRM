import { useCallback, useEffect, useRef, useState } from 'react'
import { API } from '../../api/client'
import {
  loadChatColleagues,
  loadStaffChatPreferences,
  loadStaffConversations,
  loadStaffMessages,
  markConversationRead,
  saveStaffChatPreferences,
  staffChatSocketUrl,
  type StaffChatMessage,
  type StaffChatPreferences,
  type StaffColleague,
  type StaffConversation,
  type StaffMessageEvent,
} from '../../api/staffChat'
import { useStaffChatAlerts } from './useStaffChatAlerts'

function mergeMessage(items: StaffChatMessage[], incoming: StaffChatMessage) {
  if (items.some((item) => item.id === incoming.id)) {
    return items.map((item) => item.id === incoming.id ? incoming : item)
  }
  return [...items, incoming].sort((left, right) => left.id - right.id)
}

export type StaffChatNotification = {
  key: number
  conversationId: string
  conversationTitle: string
  message: StaffChatMessage
}

/**
 * Общее состояние внутреннего чата живёт, пока открыто рабочее место сотрудника.
 * REST остаётся источником истины, а единственный WebSocket обновляет и страницу, и шапку.
 */
export function useStaffChat(apiOnline: boolean, chatOpen: boolean, onOpenChat: () => void) {
  const [conversations, setConversations] = useState<StaffConversation[]>([])
  const [colleagues, setColleagues] = useState<StaffColleague[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [messages, setMessages] = useState<StaffChatMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [connected, setConnected] = useState(false)
  const [error, setError] = useState('')
  const [notification, setNotification] = useState<StaffChatNotification | null>(null)
  const [preferences, setPreferences] = useState<StaffChatPreferences>({ notificationsEnabled: true, soundEnabled: true, browserEnabled: false })
  const selectedRef = useRef<string | null>(null)
  const messagesRef = useRef<StaffChatMessage[]>([])
  const historyLoadingRef = useRef(false)
  const chatOpenRef = useRef(chatOpen)
  const conversationsRef = useRef<StaffConversation[]>([])
  const preferencesRef = useRef(preferences)
  const notificationSequence = useRef(0)

  useEffect(() => {
    chatOpenRef.current = chatOpen
  }, [chatOpen])

  useEffect(() => {
    preferencesRef.current = preferences
  }, [preferences])

  useEffect(() => {
    messagesRef.current = messages
  }, [messages])

  const reloadConversations = useCallback(async () => {
    const items = await loadStaffConversations()
    conversationsRef.current = items
    setConversations(items)
  }, [])

  const markLatestRead = useCallback(async (conversationId: string, items: StaffChatMessage[]) => {
    const latest = items.at(-1)
    if (!latest) return
    const read = await markConversationRead(conversationId, latest.id)
    if (selectedRef.current === conversationId) {
      setMessages((current) => mergeMessage(current, read))
    }
    setConversations((current) => current.map((item) => item.id === conversationId ? { ...item, unreadCount: 0 } : item))
  }, [])

  const openConversation = useCallback(async (conversationId: string) => {
    selectedRef.current = conversationId
    setSelectedId(conversationId)
    setMessagesLoading(true)
    setHasMore(false)
    setError('')
    setNotification((current) => current?.conversationId === conversationId ? null : current)
    try {
      const history = await loadStaffMessages(conversationId)
      if (selectedRef.current !== conversationId) return
      setMessages(history)
      setHasMore(history.length === 200)
      await markLatestRead(conversationId, history)
    } catch (reason) {
      setError((reason as Error).message)
    } finally {
      if (selectedRef.current === conversationId) setMessagesLoading(false)
    }
  }, [markLatestRead])

  const clearConversation = useCallback(() => {
    selectedRef.current = null
    setSelectedId(null)
    setMessages([])
    setMessagesLoading(false)
    setHistoryLoading(false)
    setHasMore(false)
  }, [])

  const appendMessage = useCallback((incoming: StaffChatMessage) => {
    setMessages((items) => mergeMessage(items, incoming))
  }, [])

  const loadEarlier = useCallback(async (conversationId: string) => {
    const beforeId = messagesRef.current[0]?.id
    if (!beforeId || historyLoadingRef.current || selectedRef.current !== conversationId) return
    historyLoadingRef.current = true
    setHistoryLoading(true)
    try {
      const older = await loadStaffMessages(conversationId, beforeId)
      if (selectedRef.current !== conversationId) return
      setMessages((current) => {
        const loaded = new Set(current.map((item) => item.id))
        return [...older.filter((item) => !loaded.has(item.id)), ...current]
      })
      setHasMore(older.length === 200)
    } catch (reason) {
      if (selectedRef.current === conversationId) setError((reason as Error).message)
    } finally {
      historyLoadingRef.current = false
      if (selectedRef.current === conversationId) setHistoryLoading(false)
    }
  }, [])

  const dismissNotification = useCallback(() => setNotification(null), [])

  const updatePreferences = useCallback(async (next: StaffChatPreferences) => {
    const saved = await saveStaffChatPreferences(next)
    preferencesRef.current = saved
    setPreferences(saved)
    if (!saved.notificationsEnabled) setNotification(null)
    return saved
  }, [])

  const openAlertConversation = useCallback(async (conversationId: string) => {
    onOpenChat()
    await openConversation(conversationId)
  }, [onOpenChat, openConversation])

  useStaffChatAlerts(notification, preferences, openAlertConversation)

  useEffect(() => {
    if (!apiOnline) return
    let active = true
    Promise.all([loadStaffConversations(), loadChatColleagues(), loadStaffChatPreferences()])
      .then(([chatList, staffList, savedPreferences]) => {
        if (!active) return
        conversationsRef.current = chatList
        setConversations(chatList)
        setColleagues(staffList)
        preferencesRef.current = savedPreferences
        setPreferences(savedPreferences)
      })
      .catch((reason) => { if (active) setError((reason as Error).message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [apiOnline])

  useEffect(() => {
    if (!apiOnline) return
    let disposed = false
    let socket: WebSocket | null = null
    let reconnect: ReturnType<typeof setTimeout> | null = null
    const connect = () => {
      socket = new WebSocket(staffChatSocketUrl(API))
      socket.onopen = () => {
        if (disposed) return
        setConnected(true)
        void reloadConversations().catch(() => undefined)
        const current = selectedRef.current
        if (chatOpenRef.current && current) void openConversation(current)
      }
      socket.onmessage = (event) => {
        if (disposed) return
        const incoming = JSON.parse(String(event.data)) as StaffMessageEvent
        void reloadConversations().catch(() => undefined)
        const isSelected = selectedRef.current === incoming.message.conversationId
        if (incoming.type === 'MESSAGE_UPDATED') {
          if (isSelected) appendMessage(incoming.message)
          return
        }
        if (isSelected) appendMessage(incoming.message)
        if (chatOpenRef.current && isSelected) {
          if (!incoming.message.own) {
            void markConversationRead(incoming.message.conversationId, incoming.message.id)
              .then(reloadConversations)
              .catch(() => undefined)
          }
        } else if (!incoming.message.own) {
          const conversationTitle = conversationsRef.current.find((item) => item.id === incoming.message.conversationId)?.title ?? incoming.message.senderName
          notificationSequence.current += 1
          const nextNotification = {
            key: notificationSequence.current,
            conversationId: incoming.message.conversationId,
            conversationTitle,
            message: incoming.message,
          }
          if (preferencesRef.current.notificationsEnabled) setNotification(nextNotification)
        }
      }
      socket.onclose = () => {
        if (disposed) return
        setConnected(false)
        reconnect = setTimeout(connect, 3000)
      }
      socket.onerror = () => socket?.close()
    }
    connect()
    return () => {
      disposed = true
      if (reconnect) clearTimeout(reconnect)
      if (socket) {
        socket.onclose = null
        socket.close()
      }
    }
  }, [apiOnline, appendMessage, openConversation, reloadConversations])

  const unreadCount = conversations.reduce((total, item) => total + item.unreadCount, 0)

  return {
    conversations,
    colleagues,
    selectedId,
    messages,
    loading,
    messagesLoading,
    historyLoading,
    hasMore,
    connected,
    error,
    notification,
    unreadCount,
    preferences,
    setError,
    reloadConversations,
    openConversation,
    clearConversation,
    appendMessage,
    loadEarlier,
    dismissNotification,
    updatePreferences,
  }
}

export type StaffChatState = ReturnType<typeof useStaffChat>
