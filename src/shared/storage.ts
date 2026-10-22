/** Чтение и запись localStorage, которые не падают в приватном режиме браузера. */
export function readStored<T extends string>(key: string, parse: (value: string | null) => T): T {
  try {
    return parse(localStorage.getItem(key))
  } catch {
    return parse(null)
  }
}

export function writeStored(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // хранилище недоступно — выбор просто не запомнится
  }
}
