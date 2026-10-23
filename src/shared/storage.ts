/**
 * Ключ localStorage: текущий alphacrm-* и прежний quiet-clinic-* — выбор, сохранённый до
 * переименования продукта, не теряется. Читаем сначала новый, потом прежний; пишем только новый.
 */
export type StoredKey = { current: string; legacy?: string }

export const storageKeys = {
  colorMode: { current: 'alphacrm-color-mode', legacy: 'quiet-clinic-color-mode' },
  scheduleDate: { current: 'alphacrm-schedule-date', legacy: 'quiet-clinic-schedule-date' },
} satisfies Record<string, StoredKey>

function readRaw(key: StoredKey): string | null {
  const value = localStorage.getItem(key.current)
  if (value !== null || !key.legacy) return value
  return localStorage.getItem(key.legacy)
}

/** Чтение и запись localStorage, которые не падают в приватном режиме браузера. */
export function readStored<T>(key: StoredKey, parse: (value: string | null) => T): T {
  try {
    return parse(readRaw(key))
  } catch {
    return parse(null)
  }
}

export function writeStored(key: StoredKey, value: string) {
  try {
    localStorage.setItem(key.current, value)
  } catch {
    // хранилище недоступно — выбор просто не запомнится
  }
}
