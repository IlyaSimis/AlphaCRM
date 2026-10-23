import { describe, expect, it } from 'vitest'
import { readStored, storageKeys, writeStored } from './storage'

const read = () => readStored(storageKeys.colorMode, (value) => value)

describe('localStorage keys after the rename to AlphaCRM', () => {
  it('reads the new alphacrm-* key first', () => {
    localStorage.setItem('quiet-clinic-color-mode', 'light')
    localStorage.setItem('alphacrm-color-mode', 'dark')
    expect(read()).toBe('dark')
  })

  it('falls back to the old quiet-clinic-* key when the new one is missing', () => {
    localStorage.setItem('quiet-clinic-color-mode', 'dark')
    expect(read()).toBe('dark')
  })

  it('writes only under the new key', () => {
    localStorage.setItem('quiet-clinic-schedule-date', '2026-10-01')
    writeStored(storageKeys.scheduleDate, '2026-10-20')
    expect(localStorage.getItem('alphacrm-schedule-date')).toBe('2026-10-20')
    expect(localStorage.getItem('quiet-clinic-schedule-date')).toBe('2026-10-01')
    expect(readStored(storageKeys.scheduleDate, (value) => value)).toBe('2026-10-20')
  })
})
