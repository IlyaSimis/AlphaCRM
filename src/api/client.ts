import { errorPhrase } from './errorMessages'

export type ApiRole = 'OWNER' | 'SENIOR_ADMINISTRATOR' | 'ADMINISTRATOR' | 'MANAGER' | 'DOCTOR' | 'NURSE' | 'SENIOR_NURSE' | 'CASHIER' | 'SUPPORT'

/** Тот же origin, что и интерфейс: nginx в Docker, прокси Vite при разработке. */
export const API = import.meta.env.VITE_API_URL ?? '/api/v1'

/** Демо-врачи из миграций: остаются в расписании, но учётных записей у них нет. */
export const PETROV_ID = '00000000-0000-0000-0000-000000000201'
export const LEBEDEVA_ID = '00000000-0000-0000-0000-000000000202'

const CSRF_COOKIE = 'XSRF-TOKEN'
const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']

let unauthorizedHandler: ((code?: string) => void) | null = null

/** Вызывается, когда живой API ответил 401: сессия закончилась — вернуться на страницу входа. */
export function onUnauthorized(handler: ((code?: string) => void) | null) {
  unauthorizedHandler = handler
}

function readCookie(name: string) {
  const match = document.cookie.split('; ').find((part) => part.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

/** Cookie XSRF-TOKEN выдаёт сервер; если её ещё нет, запрашиваем перед изменяющим запросом. */
async function csrfToken() {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) return existing
  try {
    await fetch(`${API}/auth/csrf`, { credentials: 'include' })
  } catch {
    return null
  }
  return readCookie(CSRF_COOKIE)
}

/**
 * Все запросы к API: cookie сессии (credentials: 'include'), CSRF-заголовок на изменяющих
 * запросах, возврат на вход при 401. Роль и автор действия берёт сервер из сессии.
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  // FormData (загрузка файла) — без Content-Type: границу multipart браузер подставит сам.
  const json = Boolean(init.body) && !(typeof FormData !== 'undefined' && init.body instanceof FormData)
  const headers: Record<string, string> = { ...(json ? { 'Content-Type': 'application/json' } : {}), ...((init.headers as Record<string, string> | undefined) ?? {}) }
  if (!SAFE_METHODS.includes(method)) {
    const token = await csrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }
  const response = await fetch(`${API}${path}`, { ...init, method, headers, credentials: 'include' })
  if (response.status === 401 && !path.startsWith('/auth/')) {
    let code: string | undefined
    try {
      const body: unknown = await response.clone().json()
      if (body && typeof body === 'object' && 'code' in body && typeof body.code === 'string') {
        code = body.code
      }
    } catch {
      // Нестандартный ответ прокси: достаточно обычного возврата на страницу входа.
    }
    unauthorizedHandler?.(code)
  }
  return response
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(message: string, status: number, code: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

/**
 * Превращает неуспешный ответ в ApiError. Текст для сотрудника: фраза экрана по коду ответа,
 * иначе сообщение сервера, иначе запасной текст. Сырой code на экран не попадает.
 */
export async function failure(response: Response, fallback: string, byStatus: Record<number, string> = {}) {
  let body: { code?: unknown; message?: unknown } | null = null
  try {
    body = await response.json()
  } catch {
    body = null
  }
  if (!body || typeof body !== 'object') {
    // Ответ не от API (nginx, прокси): показываем HTTP-статус, чтобы причину можно было найти.
    return new ApiError(byStatus[response.status] ?? `${fallback} (код ${response.status})`, response.status, 'ERROR')
  }
  const code = typeof body.code === 'string' && body.code ? body.code : 'ERROR'
  const serverMessage = typeof body.message === 'string' && body.message ? body.message : undefined
  const message = errorPhrase(code) ?? serverMessage ?? byStatus[response.status] ?? fallback
  return new ApiError(message, response.status, code)
}
