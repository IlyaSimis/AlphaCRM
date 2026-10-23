import { apiFetch, failure } from './client'
import type { TaskItem, WaitlistItem } from './types'

/** Открытые задачи со сроком до dueTo включительно (просроченные тоже). */
export async function loadTasksDue(dueTo: string): Promise<TaskItem[]> {
  const response = await apiFetch(`/registry/tasks?${new URLSearchParams({ dueTo })}`)
  if (!response.ok) throw await failure(response, 'Задачи недоступны')
  return response.json()
}

export async function loadPatientTasks(patientId: string): Promise<TaskItem[]> {
  const response = await apiFetch(`/registry/tasks?${new URLSearchParams({ patientId })}`)
  if (!response.ok) throw await failure(response, 'Задачи недоступны')
  return response.json()
}

export async function createTask(input: { title: string; dueDate: string; assigneeId: string; patientId: string | null }): Promise<TaskItem> {
  const response = await apiFetch('/registry/tasks', { method: 'POST', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось создать задачу')
  return response.json()
}

export async function setTaskDone(id: string, done: boolean): Promise<TaskItem> {
  const response = await apiFetch(`/registry/tasks/${id}/${done ? 'done' : 'reopen'}`, { method: 'POST' })
  if (!response.ok) throw await failure(response, 'Не удалось изменить задачу')
  return response.json()
}

export async function loadWaitlist(): Promise<WaitlistItem[]> {
  const response = await apiFetch('/registry/waitlist')
  if (!response.ok) throw await failure(response, 'Лист ожидания недоступен')
  return response.json()
}

export async function addToWaitlist(input: { patientId: string; practitionerId: string; serviceId: string; wishFrom: string; wishTo: string }): Promise<WaitlistItem> {
  const response = await apiFetch('/registry/waitlist', { method: 'POST', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось добавить в лист ожидания')
  return response.json()
}

/** Запись уже создана обычным POST /appointments; ожидание закрывается ссылкой на неё. */
export async function bookFromWaitlist(id: string, appointmentId: string): Promise<WaitlistItem> {
  const response = await apiFetch(`/registry/waitlist/${id}/book`, { method: 'POST', body: JSON.stringify({ appointmentId }) })
  if (!response.ok) throw await failure(response, 'Не удалось закрыть ожидание')
  return response.json()
}

export async function cancelWaitlist(id: string): Promise<WaitlistItem> {
  const response = await apiFetch(`/registry/waitlist/${id}/cancel`, { method: 'POST' })
  if (!response.ok) throw await failure(response, 'Не удалось закрыть ожидание')
  return response.json()
}
