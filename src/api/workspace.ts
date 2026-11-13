import { apiFetch, failure } from './client'
import type { WorkspaceAssignee, WorkspaceItem, WorkspaceItemInput, WorkspaceView } from './types'

export async function loadWorkspaceAssignees(): Promise<WorkspaceAssignee[]> {
  const response = await apiFetch('/workspace/assignees')
  if (!response.ok) throw await failure(response, 'Список сотрудников недоступен')
  return response.json()
}

export async function loadWorkspaceItems(archived = false): Promise<WorkspaceItem[]> {
  const response = await apiFetch(`/workspace/items?archived=${archived}`)
  if (!response.ok) throw await failure(response, 'Рабочее пространство недоступно')
  return response.json()
}

export async function createWorkspaceItem(input: WorkspaceItemInput): Promise<WorkspaceItem> {
  const response = await apiFetch('/workspace/items', { method: 'POST', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось создать запись')
  return response.json()
}

export async function updateWorkspaceItem(id: string, input: WorkspaceItemInput): Promise<WorkspaceItem> {
  const response = await apiFetch(`/workspace/items/${id}`, { method: 'PUT', body: JSON.stringify(input) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить запись')
  return response.json()
}

export async function archiveWorkspaceItem(id: string, archived: boolean): Promise<WorkspaceItem> {
  const response = await apiFetch(`/workspace/items/${id}/${archived ? 'archive' : 'restore'}`, { method: 'POST' })
  if (!response.ok) throw await failure(response, archived ? 'Не удалось архивировать запись' : 'Не удалось восстановить запись')
  return response.json()
}

export async function loadWorkspaceViews(): Promise<WorkspaceView[]> {
  const response = await apiFetch('/workspace/views')
  if (!response.ok) throw await failure(response, 'Сохранённые представления недоступны')
  return response.json()
}

export async function createWorkspaceView(name: string, filter: WorkspaceView['filter']): Promise<WorkspaceView> {
  const response = await apiFetch('/workspace/views', { method: 'POST', body: JSON.stringify({ name, filter }) })
  if (!response.ok) throw await failure(response, 'Не удалось сохранить представление')
  return response.json()
}

export async function deleteWorkspaceView(id: string): Promise<void> {
  const response = await apiFetch(`/workspace/views/${id}`, { method: 'DELETE' })
  if (!response.ok) throw await failure(response, 'Не удалось удалить представление')
}
