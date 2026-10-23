// Применяет тему до первой отрисовки, чтобы не было вспышки светлого фона.
(() => {
  let stored = null
  try {
    // Новый ключ alphacrm-*, если его ещё нет — прежний quiet-clinic-* (см. src/shared/storage.ts).
    stored = localStorage.getItem('alphacrm-color-mode') ?? localStorage.getItem('quiet-clinic-color-mode')
  } catch {
    // хранилище недоступно (приватный режим) — берём системную тему
  }
  const mode = stored === 'light' || stored === 'dark'
    ? stored
    : matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  document.documentElement.dataset.theme = mode
  document.documentElement.style.colorScheme = mode
})()
