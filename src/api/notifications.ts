import { apiFetch, failure } from './client'

export type NotificationSettings = {
  reminder24hEnabled: boolean
  reminder3hEnabled: boolean
}

const ROUTE = '/clinic/notification-settings'

export async function loadNotificationSettings(): Promise<NotificationSettings> {
  const response = await apiFetch(ROUTE)
  if (!response.ok) throw await failure(response, 'Настройки напоминаний недоступны')
  return response.json()
}

export async function saveNotificationSettings(settings: NotificationSettings): Promise<NotificationSettings> {
  const response = await apiFetch(ROUTE, { method: 'PUT', body: JSON.stringify(settings) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить напоминания')
  return response.json()
}
