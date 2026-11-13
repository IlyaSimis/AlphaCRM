import { apiFetch, failure } from './client'
import type { InventoryCategory, InventoryItem, ItemKind, MarkingCodeItem, MarkingOperationItem, MarkingStatus, MarkingSystem, ScannedCode, StockBalance, StockDocument, StockDocumentType, StockMetrics, Warehouse } from './types'

async function json<T>(path: string, fallback: string, init?: RequestInit): Promise<T> {
  const response = await apiFetch(path, init)
  if (!response.ok) throw await failure(response, fallback)
  return response.json()
}

export const loadCategories = () => json<InventoryCategory[]>('/inventory/categories', 'Категории недоступны')

export function createCategory(input: { name: string; parentId: string | null; kind: ItemKind }) {
  return json<InventoryCategory>('/inventory/categories', 'Не удалось создать категорию', { method: 'POST', body: JSON.stringify(input) })
}

export type ItemFilter = { category?: string; kind?: string; marked?: boolean | null; q?: string; sort?: string }

export function loadItems(filter: ItemFilter = {}) {
  const query = new URLSearchParams()
  if (filter.category) query.set('category', filter.category)
  if (filter.kind) query.set('kind', filter.kind)
  if (filter.marked != null) query.set('marked', String(filter.marked))
  if (filter.q) query.set('q', filter.q)
  if (filter.sort) query.set('sort', filter.sort)
  const suffix = query.toString() ? `?${query}` : ''
  return json<InventoryItem[]>(`/inventory/items${suffix}`, 'Номенклатура недоступна')
}

/** categoryId null — основная категория вида kind (сервер заведёт её сам). */
export type ItemInput = { categoryId: string | null; kind?: ItemKind; name: string; unit: string; gtin: string | null; marked: boolean; markingSystem: MarkingSystem; rxOnly: boolean; active?: boolean; minStock?: number }

export function saveItem(id: string | null, input: ItemInput) {
  return json<InventoryItem>(id ? `/inventory/items/${id}` : '/inventory/items', 'Не удалось сохранить позицию', { method: id ? 'PUT' : 'POST', body: JSON.stringify(input) })
}

export type ReceiptLine =
  | { itemId: string; quantity: number; batch?: string | null; expiresOn?: string | null; codes?: string[] }
  | { name: string; kind?: ItemKind; unit?: string | null; gtin?: string | null; quantity: number; batch?: string | null; expiresOn?: string | null }

export type ReceiptResult = { document: StockDocument; posted: boolean; itemsCreated: number; waitingCodes: string[] }

/** Приход в одно действие: новые позиции заводятся, накладная сразу проводится. */
export function receiveStock(input: { warehouseId?: string | null; supplier?: string | null; documentDate?: string | null; idempotencyKey: string; lines: ReceiptLine[] }) {
  return json<ReceiptResult>('/inventory/receipts', 'Не удалось провести поступление', { method: 'POST', body: JSON.stringify(input) })
}

export const loadWarehouses = () => json<Warehouse[]>('/inventory/warehouses', 'Склады недоступны')

export function createWarehouse(name: string) {
  return json<Warehouse>('/inventory/warehouses', 'Не удалось создать склад', { method: 'POST', body: JSON.stringify({ name }) })
}

export function loadDocuments(type?: StockDocumentType | '', status?: string) {
  const query = new URLSearchParams()
  if (type) query.set('type', type)
  if (status) query.set('status', status)
  const suffix = query.toString() ? `?${query}` : ''
  return json<StockDocument[]>(`/inventory/documents${suffix}`, 'Накладные недоступны')
}

export type DocumentInput = {
  type: StockDocumentType
  documentDate?: string | null
  supplier?: string | null
  fromWarehouseId?: string | null
  toWarehouseId?: string | null
  note?: string | null
  idempotencyKey: string
  lines: Array<{ itemId: string; quantity: number; batch?: string | null; expiresOn?: string | null; codes?: string[] }>
}

export function createDocument(input: DocumentInput) {
  return json<StockDocument>('/inventory/documents', 'Не удалось создать накладную', { method: 'POST', body: JSON.stringify(input) })
}

export function updateDocument(id: string, input: DocumentInput) {
  return json<StockDocument>(`/inventory/documents/${id}`, 'Не удалось сохранить накладную', { method: 'PUT', body: JSON.stringify(input) })
}

/** Проведение; allowNegative — расход сверх остатка (только администраторам). */
export function postDocument(id: string, allowNegative = false) {
  return json<StockDocument>(`/inventory/documents/${id}/post`, 'Не удалось провести накладную', { method: 'POST', body: JSON.stringify({ allowNegative }) })
}

export function cancelDocument(id: string) {
  return json<StockDocument>(`/inventory/documents/${id}/cancel`, 'Не удалось отменить накладную', { method: 'POST' })
}

export function loadBalances(filter: { warehouse?: string; category?: string; q?: string } = {}) {
  const query = new URLSearchParams()
  if (filter.warehouse) query.set('warehouse', filter.warehouse)
  if (filter.category) query.set('category', filter.category)
  if (filter.q) query.set('q', filter.q)
  const suffix = query.toString() ? `?${query}` : ''
  return json<StockBalance[]>(`/inventory/balances${suffix}`, 'Остатки недоступны')
}

export function loadInventoryMetrics(from: string, to: string) {
  return json<StockMetrics>(`/inventory/metrics?from=${from}&to=${to}`, 'Складские метрики недоступны')
}

/** Разбор строки со сканера DataMatrix. */
export function scanCode(code: string) {
  return json<ScannedCode>('/inventory/marking/scan', 'Не удалось прочитать код', { method: 'POST', body: JSON.stringify({ code }) })
}

export function loadMarkingCodes(status?: string) {
  return json<MarkingCodeItem[]>(`/inventory/marking/codes${status ? `?status=${status}` : ''}`, 'Марки недоступны')
}

export function loadMarkingOperations(status?: string) {
  return json<MarkingOperationItem[]>(`/inventory/marking/operations${status ? `?status=${status}` : ''}`, 'Очередь маркировки недоступна')
}

export function retryMarkingOperation(id: string) {
  return json<MarkingOperationItem>(`/inventory/marking/operations/${id}/retry`, 'Не удалось повторить операцию', { method: 'POST' })
}

export const loadMarkingStatus = () => json<MarkingStatus>('/inventory/marking/status', 'Статус маркировки недоступен')
