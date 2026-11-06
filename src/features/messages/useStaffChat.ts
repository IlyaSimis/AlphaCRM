import { useCallback, useEffect, useRef, useState } from 'react'
import { API } from '../../api/client'
import {
  loadChatColleagues,
  loadStaffConversations,
  loadStaffMessages,
  markConversationRead,
  staffChatSocketUrl,
  type StaffChatMessage,
  type StaffColleague,
  type StaffConversation,
  type StaffMessageEvent,
} from '../../api/staffChat'

function mergeMessage(items: StaffChatMessage[], incoming: StaffChatMessage) {
  if (items.some((item) => item.id === incoming.id)) return items
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
export function useStaffChat(apiOnline: boolean, chatOpen: boolean) {
  const [conversations, setConversations] = useState<StaffConversation[]>([])
  const [colleagues, setColleagues] = useState<StaffColleague[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [messages, setMessages] = useState<StaffChatMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [messagesLoading, setMessagesLoading] = useState(false)
  const [connected, setConnected] = useState(false)
  const [error, setError] = useState('')
  const [notification, setNotification] = useState<StaffChatNotification | null>(null)
  const selectedRef = useRef<string | null>(null)
  const chatOpenRef = useRef(chatOpen)
  const conversationsRef = useRef<StaffConversation[]>([])
  const notificationSequence = useRef(0)

  useEffect(() => {
    chatOpenRef.current = chatOpen
  }, [chatOpen])

  const reloadConversations = useCallback(async () => {
    const items = await loadStaffConversations()
    conversationsRef.current = items
    setConversations(items)
  }, [])

  const markLatestRead = useCallback(async (conversationId: string, items: StaffChatMessage[]) => {
    const latest = items.at(-1)
    if (!latest) return
    await markConversationRead(conversationId, latest.id)
    setConversations((current) => current.map((item) => item.id === conversationId ? { ...item, unreadCount: 0 } : item))
  }, [])

  const openConversation = useCallback(async (conversationId: string) => {
    selectedRef.current = conversationId
    setSelectedId(conversationId)
    setMessagesLoading(true)
    setError('')
    setNotification((current) => current?.conversationId === conversationId ? null : current)
    try {
      const history = await loadStaffMessages(conversationId)
      if (selectedRef.current !== conversationId) return
      setMessages(history)
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
  }, [])

  const appendMessage = useCallback((incoming: StaffChatMessage) => {
    setMessages((items) => mergeMessage(items, incoming))
  }, [])

  const dismissNotification = useCallback(() => setNotification(null), [])

  useEffect(() => {
    if (!apiOnline) return
    let active = true
    Promise.all([loadStaffConversations(), loadChatColleagues()])
      .then(([chatList, staffList]) => {
        if (!active) return
        conversationsRef.current = chatList
        setConversations(chatList)
        setColleagues(staffList)
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
        if (incoming.type !== 'MESSAGE_CREATED') return
        void reloadConversations().catch(() => undefined)
        const isSelected = selectedRef.current === incoming.message.conversationId
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
          setNotification({
            key: notificationSequence.current,
            conversationId: incoming.message.conversationId,
            conversationTitle,
            message: incoming.message,
          })
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
    connected,
    error,
    notification,
    unreadCount,
    setError,
    reloadConversations,
    openConversation,
    clearConversation,
    appendMessage,
    dismissNotification,
  }
}

export type StaffChatState = ReturnType<typeof useStaffChat>
