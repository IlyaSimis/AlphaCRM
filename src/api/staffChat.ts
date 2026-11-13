import { apiFetch, failure } from './client'
import type { StaffRole } from './types'

export type StaffColleague = { id: string; fullName: string; role: StaffRole }
export type StaffChatMember = { id: string; fullName: string; role: StaffRole; active: boolean }
export type StaffChatAttachment = {
  id: string
  name: string
  contentType: string
  sizeBytes: number
  downloadUrl: string
}
export type StaffChatReaction = { emoji: string; count: number; own: boolean }
export type StaffChatMessage = {
  id: number
  conversationId: string
  senderId: string
  senderName: string
  body: string
  createdAt: string
  mentionIds: string[]
  own: boolean
  mentioned: boolean
  readByAll: boolean
  editedAt: string | null
  deleted: boolean
  attachments: StaffChatAttachment[]
  reactions: StaffChatReaction[]
}
export type StaffChatPreferences = {
  notificationsEnabled: boolean
  soundEnabled: boolean
  browserEnabled: boolean
}
export type StaffConversation = {
  id: string
  kind: 'DIRECT' | 'GROUP'
  title: string
  members: StaffChatMember[]
  lastMessage: StaffChatMessage | null
  unreadCount: number
  updatedAt: string
}
export type StaffMessageEvent = { type: 'MESSAGE_CREATED' | 'MESSAGE_UPDATED'; message: StaffChatMessage }

async function request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const requestBody = body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body)
  const response = await apiFetch(path, { method, ...(requestBody === undefined ? {} : { body: requestBody }) })
  if (!response.ok) throw await failure(response, 'Не удалось выполнить действие в чате')
  if (response.status === 204) return undefined as T
  return response.json()
}

export function loadChatColleagues() {
  return request<StaffColleague[]>('/staff-chat/colleagues')
}

export function loadStaffConversations() {
  return request<StaffConversation[]>('/staff-chat/conversations')
}

export function loadStaffChatPreferences() {
  return request<StaffChatPreferences>('/staff-chat/preferences')
}

export function saveStaffChatPreferences(preferences: StaffChatPreferences) {
  return request<StaffChatPreferences>('/staff-chat/preferences', 'PUT', preferences)
}

export function createDirectConversation(staffId: string) {
  return request<StaffConversation>('/staff-chat/conversations/direct', 'POST', { staffId })
}

export function createGroupConversation(title: string, memberIds: string[]) {
  return request<StaffConversation>('/staff-chat/conversations/group', 'POST', { title, memberIds })
}

export function loadStaffMessages(conversationId: string, beforeId?: number) {
  const page = beforeId === undefined ? '' : `?beforeId=${beforeId}`
  return request<StaffChatMessage[]>(`/staff-chat/conversations/${conversationId}/messages${page}`)
}

export function sendStaffMessage(conversationId: string, text: string, mentionIds: string[] = [], files: File[] = []) {
  if (files.length === 0) {
    return request<StaffChatMessage>(`/staff-chat/conversations/${conversationId}/messages`, 'POST', { text, mentionIds })
  }
  const form = new FormData()
  form.append('text', text)
  mentionIds.forEach((id) => form.append('mentionIds', id))
  files.forEach((file) => form.append('files', file, file.name))
  return request<StaffChatMessage>(`/staff-chat/conversations/${conversationId}/messages`, 'POST', form)
}

export function editStaffMessage(messageId: number, text: string) {
  return request<StaffChatMessage>(`/staff-chat/messages/${messageId}`, 'PATCH', { text })
}

export function deleteStaffMessage(messageId: number) {
  return request<StaffChatMessage>(`/staff-chat/messages/${messageId}`, 'DELETE')
}

export function reactStaffMessage(messageId: number, emoji: string) {
  return request<StaffChatMessage>(`/staff-chat/messages/${messageId}/reaction`, 'PUT', { emoji })
}

export function markConversationRead(conversationId: string, messageId: number) {
  return request<StaffChatMessage>(`/staff-chat/conversations/${conversationId}/read`, 'POST', { messageId })
}

/** Тот же origin и cookie сотрудника; Vite и nginx проксируют Upgrade на backend. */
export function staffChatSocketUrl(api: string) {
  const url = new URL(`${api}/staff-chat/events`, window.location.origin)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}
