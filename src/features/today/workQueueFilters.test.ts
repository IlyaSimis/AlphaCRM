import { describe, expect, it } from 'vitest'
import { readQueueFilter, writeQueueFilter } from './workQueueFilters'

describe('work queue filters', () => {
  it('keeps a personal filter and falls back for unknown stored values', () => {
    expect(readQueueFilter('admin')).toBe('ALL')
    writeQueueFilter('admin', 'REBOOK')
    writeQueueFilter('manager', 'PAYMENT')
    expect(readQueueFilter('admin')).toBe('REBOOK')
    expect(readQueueFilter('manager')).toBe('PAYMENT')

    localStorage.setItem('alphacrm.work-queue-filter.admin', 'UNKNOWN')
    expect(readQueueFilter('admin')).toBe('ALL')
  })
})
