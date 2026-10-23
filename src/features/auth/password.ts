export const MIN_PASSWORD = 8

/** Та же проверка, что на сервере: не короче 8 символов; плюс совпадение с повтором. */
export function passwordProblem(password: string, repeat: string) {
  if (password.length < MIN_PASSWORD) return `Пароль должен быть не короче ${MIN_PASSWORD} символов`
  if (password !== repeat) return 'Пароли не совпадают'
  return null
}
