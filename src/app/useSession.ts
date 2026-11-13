import { useCallback, useEffect, useState } from 'react'
import { currentStaff, login, logout } from '../api/auth'
import { ApiError, onUnauthorized } from '../api/client'
import type { StaffItem } from '../api/types'
import { setClinicTimeZone } from '../shared/clinicTime'

/**
 * Сессия сотрудника.
 * signedIn + live — вошли на сервер, роль и автор действия берутся сервером из cookie сессии.
 * signedIn без live — демо-песочница без сервера: в запросы ничего не подставляется.
 */
export type SessionState =
  | { status: 'loading' }
  | { status: 'signedOut'; serverOnline: boolean; message?: string }
  | { status: 'signedIn'; staff: StaffItem; live: boolean }

const SERVER_DOWN = 'Сервер недоступен'

/** Вошли на сервер: время на экране — в поясе клиники из /auth/me. Песочница — UTC. */
function signedIn(staff: StaffItem, live: boolean): SessionState {
  setClinicTimeZone(live ? staff.timeZone : 'UTC')
  return { status: 'signedIn', staff, live }
}

export function useSession() {
  const [state, setState] = useState<SessionState>({ status: 'loading' })

  const check = useCallback(async () => {
    setState({ status: 'loading' })
    try {
      const staff = await currentStaff()
      setState(staff ? signedIn(staff, true) : { status: 'signedOut', serverOnline: true })
    } catch {
      setState({ status: 'signedOut', serverOnline: false })
    }
  }, [])

  useEffect(() => { void check() }, [check])

  useEffect(() => {
    // 401 от живого API: сессия истекла или сотрудника отключили — назад на вход.
    onUnauthorized((code) => setState((current) => current.status === 'signedIn' && current.live
      ? {
          status: 'signedOut',
          serverOnline: true,
          message: code === 'SESSION_REPLACED'
            ? 'Выполнен вход на другом устройстве. Войдите снова.'
            : 'Сессия закончилась. Войдите снова.',
        }
      : current))
    return () => onUnauthorized(null)
  }, [])

  /** Возвращает текст ошибки для формы или null после успешного входа. */
  const signIn = async (username: string, password: string): Promise<string | null> => {
    try {
      const staff = await login(username, password)
      setState(signedIn(staff, true))
      return null
    } catch (error) {
      if (error instanceof ApiError) return error.message
      setState({ status: 'signedOut', serverOnline: false })
      return SERVER_DOWN
    }
  }

  const enterDemo = (staff: StaffItem) => setState(signedIn(staff, false))

  const signOut = async () => {
    if (state.status === 'signedIn' && state.live) {
      try {
        await logout()
      } catch {
        // сервер пропал — cookie всё равно перестанет действовать по таймауту
      }
      setState({ status: 'signedOut', serverOnline: true })
      return
    }
    setState({ status: 'signedOut', serverOnline: false })
  }

  return { state, check, signIn, enterDemo, signOut }
}
