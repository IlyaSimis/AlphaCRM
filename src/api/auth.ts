import { ApiError, apiFetch, failure } from './client'
import { errorMessages } from './errorMessages'
import type { StaffItem } from './types'

/** Текущий сотрудник по cookie сессии. null — сервер жив, но вход не выполнен. */
export async function currentStaff(): Promise<StaffItem | null> {
  const response = await apiFetch('/auth/me')
  if (response.status === 401) return null
  if (!response.ok) throw await failure(response, 'Сервер недоступен')
  return response.json()
}

export async function login(username: string, password: string): Promise<StaffItem> {
  const response = await apiFetch('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })
  // На 401 сервер отвечает одинаково для неизвестного логина и неверного пароля.
  if (response.status === 401) throw new ApiError(errorMessages.INVALID_CREDENTIALS, 401, 'INVALID_CREDENTIALS')
  if (!response.ok) throw await failure(response, 'Не удалось войти')
  return response.json()
}

export async function logout() {
  await apiFetch('/auth/logout', { method: 'POST' })
}

export async function changePassword(currentPassword: string, newPassword: string) {
  const response = await apiFetch('/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) })
  if (!response.ok) throw await failure(response, 'Не удалось сменить пароль')
}
