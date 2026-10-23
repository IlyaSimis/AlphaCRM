import { useEffect, useState } from 'react'

/**
 * Демо-песочница без сервера: изменения живут в памяти вкладки, пока её не обновят,
 * и переживают смену демо-сотрудника. На живом API песочница не читается и не пишется.
 */
const store = new Map<string, unknown>()

export function useSandboxState<T>(key: string, initial: T, enabled: boolean): [T, (value: T | ((current: T) => T)) => void] {
  const [value, setValue] = useState<T>(() => (enabled && store.has(key) ? (store.get(key) as T) : initial))
  useEffect(() => {
    if (enabled) store.set(key, value)
  }, [enabled, key, value])
  return [value, setValue]
}

/** Сбрасывает песочницу: для тестов и «начать демо заново». */
export function resetSandbox() {
  store.clear()
}
