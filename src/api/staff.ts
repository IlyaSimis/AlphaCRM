import { apiFetch, failure } from './client'
import type { StaffItem, StaffRole } from './types'

export async function loadStaff(): Promise<StaffItem[]> {
  const response = await apiFetch('/staff')
  if (!response.ok) throw await failure(response, 'Список сотрудников недоступен')
  return response.json()
}

export async function createStaff(input: { fullName: string; username: string; password: string; role: StaffRole; practitionerId: string | null }): Promise<StaffItem> {
  const response = await apiFetch('/staff', { method: 'POST', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось создать сотрудника')
  return response.json()
}

export async function setStaffActive(id: string, active: boolean): Promise<StaffItem> {
  const response = await apiFetch(`/staff/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' })
  if (!response.ok) throw await failure(response, 'Не удалось изменить сотрудника')
  return response.json()
}

export async function resetStaffPassword(id: string, newPassword: string) {
  const response = await apiFetch(`/staff/${id}/password`, { method: 'POST', body: JSON.stringify({ newPassword }) })
  if (!response.ok) throw await failure(response, 'Не удалось задать пароль')
}
