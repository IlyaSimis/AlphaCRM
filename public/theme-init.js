// Применяет тему до первой отрисовки, чтобы не было вспышки светлого фона.
(() => {
  let stored = null
  try {
    stored = localStorage.getItem('quiet-clinic-color-mode')
  } catch {
    // хранилище недоступно (приватный режим) — берём системную тему
  }
  const mode = stored === 'light' || stored === 'dark'
    ? stored
    : matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  document.documentElement.dataset.theme = mode
  document.documentElement.style.colorScheme = mode
})()
