import { apiFetch, failure } from './client'
import type { DriveListing, DriveNode, DrivePermission, DrivePrincipal, DriveQuota, DriveShare, DriveVersion } from './types'

async function json<T>(path: string, fallback: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw await failure(response, fallback)
  return response.json()
}

/** Без папки — «Мой диск»; с папкой — путь и содержимое. */
export function loadFolder(parentId: string | null) {
  return json<DriveListing>(parentId ? `/drive/nodes?parent=${parentId}` : '/drive/nodes', 'Папка недоступна')
}

export const loadShared = () => json<DriveNode[]>('/drive/shared-with-me', 'Список недоступен')
export const loadTrash = () => json<DriveNode[]>('/drive/trash', 'Корзина недоступна')
export const loadQuota = () => json<DriveQuota>('/drive/quota', 'Квота недоступна')
export const loadPrincipals = () => json<DrivePrincipal[]>('/drive/principals', 'Сотрудники недоступны')

export function createFolder(parentId: string | null, name: string) {
  return json<DriveNode>('/drive/folders', 'Не удалось создать папку', { method: 'POST', body: JSON.stringify({ parentId, name }) })
}

export function uploadFile(parentId: string | null, file: File) {
  const form = new FormData()
  form.append('file', file)
  if (parentId) form.append('parentId', parentId)
  return json<DriveNode>('/drive/files', 'Не удалось загрузить файл', { method: 'POST', body: form })
}

export function uploadVersion(id: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  return json<DriveNode>(`/drive/files/${id}/versions`, 'Не удалось загрузить версию', { method: 'POST', body: form })
}

export const loadVersions = (id: string) => json<DriveVersion[]>(`/drive/files/${id}/versions`, 'Версии недоступны')

export function renameNode(id: string, name: string) {
  return json<DriveNode>(`/drive/nodes/${id}`, 'Не удалось переименовать', { method: 'PUT', body: JSON.stringify({ name }) })
}

export function trashNode(id: string) {
  return json<DriveNode>(`/drive/nodes/${id}`, 'Не удалось удалить', { method: 'DELETE' })
}

export function restoreNode(id: string) {
  return json<DriveNode>(`/drive/nodes/${id}/restore`, 'Не удалось восстановить', { method: 'POST' })
}

export const loadShares = (id: string) => json<DriveShare[]>(`/drive/nodes/${id}/shares`, 'Права недоступны')

export function addShare(id: string, input: { principalType: DriveShare['principalType']; principalId?: string | null; permission: DrivePermission; expiresAt?: string | null }) {
  return json<DriveShare>(`/drive/nodes/${id}/shares`, 'Не удалось выдать доступ', { method: 'POST', body: JSON.stringify(input) })
}

export async function removeShare(id: string, shareId: string) {
  const response = await apiFetch(`/drive/nodes/${id}/shares/${shareId}`, { method: 'DELETE' })
  if (!response.ok) throw await failure(response, 'Не удалось отозвать доступ')
}

export function setQuota(bytesLimit: number) {
  return json<DriveQuota>('/drive/quota', 'Не удалось изменить квоту', { method: 'PUT', body: JSON.stringify({ bytesLimit }) })
}

/** Скачивание через сессию сотрудника: файл отдаётся вложением. */
export async function downloadFile(node: DriveNode, version?: number) {
  const response = await apiFetch(`/drive/files/${node.id}/content${version ? `?version=${version}` : ''}`)
  if (!response.ok) throw await failure(response, 'Не удалось скачать файл')
  saveBlob(await response.blob(), node.name)
}

/** Ссылка для сотрудников клиники: открывается только после входа. */
export function linkAddress(token: string) {
  return `${window.location.origin}/?drive-link=${encodeURIComponent(token)}`
}

/** Узел по ссылке; nodeId — вложенная папка внутри папки, открытой по ссылке. */
export const loadLink = (token: string, nodeId: string | null = null) => json<DriveListing>(`/drive/links/${encodeURIComponent(token)}${nodeId ? `?node=${nodeId}` : ''}`, 'Ссылка недоступна')

export async function downloadByLink(token: string, node: DriveNode) {
  const response = await apiFetch(`/drive/links/${encodeURIComponent(token)}/content?node=${node.id}`)
  if (!response.ok) throw await failure(response, 'Не удалось скачать файл')
  saveBlob(await response.blob(), node.name)
}

function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

