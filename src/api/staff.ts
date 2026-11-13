import { apiFetch, failure } from './client'
import type { Practitioner, StaffItem, StaffRole } from './types'

/** Карточка врача, которая создаётся вместе с учётной записью; ФИО — то же, что у сотрудника. */
export type NewPractitionerInput = { specialty: string; room: string; shifts: Practitioner['workingHours'] }

export type CreateStaffInput = { fullName: string; username: string; password: string; role: StaffRole; practitionerId: string | null; newPractitioner?: NewPractitionerInput }

export async function loadStaff(): Promise<StaffItem[]> {
  const response = await apiFetch('/staff')
  if (!response.ok) throw await failure(response, 'Список сотрудников недоступен')
  return response.json()
}

export async function createStaff(input: CreateStaffInput): Promise<StaffItem> {
  const body = input.newPractitioner
    ? { ...input, newPractitioner: { ...input.newPractitioner, shifts: input.newPractitioner.shifts.map((shift) => ({ dayOfWeek: shift.dayOfWeek, startsAt: shift.startsAt.slice(0, 5), endsAt: shift.endsAt.slice(0, 5) })) } }
    : input
  const response = await apiFetch('/staff', { method: 'POST', body: JSON.stringify(body) })
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

/** Все роли сотрудника: права складываются; менять можно роли не выше своих, свои — нельзя. */
export async function setStaffRoles(id: string, roles: StaffRole[]): Promise<StaffItem> {
  const response = await apiFetch(`/staff/${id}/roles`, { method: 'PUT', body: JSON.stringify({ roles }) })
  if (!response.ok) throw await failure(response, 'Не удалось изменить роли')
  return response.json()
}
