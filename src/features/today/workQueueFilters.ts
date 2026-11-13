import type { WorkQueueKind } from '../../api/types'
import { readStored, writeStored, type StoredKey } from '../../shared/storage'

export type QueueFilter = 'ALL' | WorkQueueKind

const filters: QueueFilter[] = ['ALL', 'CONFIRMATION', 'PAYMENT', 'REBOOK', 'NO_SHOW', 'DELIVERY']

function storageKey(selfId: string): StoredKey {
  return { current: `alphacrm.work-queue-filter.${selfId}` }
}

function isQueueFilter(value: string | null): value is QueueFilter {
  return value !== null && filters.includes(value as QueueFilter)
}

/** Фильтр личный: администраторы одной клиники не меняют рабочее представление друг друга. */
export function readQueueFilter(selfId: string): QueueFilter {
  return readStored(storageKey(selfId), (value) => isQueueFilter(value) ? value : 'ALL')
}

export function writeQueueFilter(selfId: string, filter: QueueFilter) {
  writeStored(storageKey(selfId), filter)
}
