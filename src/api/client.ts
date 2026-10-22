export type ApiRole = 'ADMINISTRATOR' | 'DOCTOR' | 'MANAGER' | 'CASHIER'

export const API = import.meta.env.VITE_API_URL ?? 'http://127.0.0.1:8081/api/v1'

export const PETROV_ID = '00000000-0000-0000-0000-000000000201'
export const LEBEDEVA_ID = '00000000-0000-0000-0000-000000000202'

/** Демо-сотрудники: от их имени уходят запросы, чтобы журнал аудита показывал реального автора действия. */
export const ACTORS: Record<ApiRole, { id: string; name: string }> = {
  ADMINISTRATOR: { id: '00000000-0000-0000-0000-000000000901', name: 'Елена Смирнова' },
  DOCTOR: { id: PETROV_ID, name: 'Александр Петров' },
  MANAGER: { id: '00000000-0000-0000-0000-000000000902', name: 'Игорь Кузнецов' },
  CASHIER: { id: '00000000-0000-0000-0000-000000000903', name: 'Наталья Орлова' },
}

/** Врач, от имени которого работает роль «Врач». По умолчанию — Александр Петров. */
let doctorActorId = PETROV_ID

export function setDoctorActor(id: string) {
  doctorActorId = id
}

export function actorIdFor(role: ApiRole) {
  return role === 'DOCTOR' ? doctorActorId : ACTORS[role].id
}

export function headersFor(role: ApiRole = 'DOCTOR') {
  return { 'Content-Type': 'application/json', 'X-Demo-Role': role, 'X-Actor-Id': actorIdFor(role) }
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

/** Превращает неуспешный ответ в ApiError с сообщением сервера или запасным текстом. */
export async function failure(response: Response, fallback: string, byStatus: Record<number, string> = {}) {
  let message = fallback
  let code = 'ERROR'
  try {
    const body = await response.json()
    message = body.message || message
    code = body.code || code
  } catch {
    message = byStatus[response.status] ?? message
  }
  return new ApiError(message, response.status, code)
}
