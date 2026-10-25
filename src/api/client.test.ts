import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, failure, onUnauthorized } from './client'

const ok = (status = 200) => ({ ok: status < 300, status, json: async () => ({}) })

afterEach(() => onUnauthorized(null))

describe('apiFetch', () => {
  it('sends the session cookie, no role, and a CSRF header only on changing requests', async () => {
    document.cookie = 'XSRF-TOKEN=abc; path=/'
    const fetchMock = vi.fn().mockResolvedValue(ok())
    vi.stubGlobal('fetch', fetchMock)
    await apiFetch('/patients')
    await apiFetch('/patients', { method: 'POST', body: '{}' })
    const [getUrl, get] = fetchMock.mock.calls[0]
    const [, post] = fetchMock.mock.calls[1]
    expect(getUrl).toBe('/api/v1/patients')
    expect(get.credentials).toBe('include')
    expect(get.headers).toEqual({})
    expect(post.headers).toEqual({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'abc' })
  })

  it('asks the server for a CSRF cookie when there is none', async () => {
    const fetchMock = vi.fn(async (url: string) => {
      if (url.endsWith('/auth/csrf')) document.cookie = 'XSRF-TOKEN=fresh; path=/'
      return ok(url.endsWith('/auth/csrf') ? 204 : 200)
    })
    vi.stubGlobal('fetch', fetchMock)
    await apiFetch('/auth/logout', { method: 'POST' })
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/v1/auth/csrf', '/api/v1/auth/logout'])
    expect((fetchMock.mock.calls[1] as unknown as [string, RequestInit])[1].headers).toMatchObject({ 'X-XSRF-TOKEN': 'fresh' })
  })

  it('a 401 outside /auth/ returns to sign-in, a 401 on sign-in does not', async () => {
    const handler = vi.fn()
    onUnauthorized(handler)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok(401)))
    await apiFetch('/auth/me')
    expect(handler).not.toHaveBeenCalled()
    await apiFetch('/appointments?date=2026-10-20')
    expect(handler).toHaveBeenCalledTimes(1)
  })
})

const reply = (status: number, body: unknown) => new Response(typeof body === 'string' ? body : JSON.stringify(body), { status })

describe('failure', () => {
  it('picks the on-screen phrase by code', async () => {
    const error = await failure(reply(409, { code: 'SERVICE_INACTIVE', message: 'The service is switched off' }), 'fallback')
    expect(error.code).toBe('SERVICE_INACTIVE')
    expect(error.message).toBe('Услуга выключена и недоступна для новой записи')
  })

  it('explains a sign-in lockout after too many attempts', async () => {
    const error = await failure(reply(429, { code: 'TOO_MANY_LOGIN_ATTEMPTS', message: 'Too many failed sign-in attempts' }), 'fallback')
    expect(error.status).toBe(429)
    expect(error.message).toContain('Слишком много неудачных попыток входа')
  })

  it('shows the server message, not the raw code, when the screen does not know the code', async () => {
    const error = await failure(reply(409, { code: 'BRAND_NEW_CODE', message: 'Something new happened' }), 'fallback')
    expect(error.message).toBe('Something new happened')
    expect(error.message).not.toContain('BRAND_NEW_CODE')
  })

  it('falls back to the screen text and HTTP status for a non-API answer', async () => {
    const error = await failure(reply(502, '<html>bad gateway</html>'), 'Не удалось войти')
    expect(error.message).toBe('Не удалось войти (код 502)')
    expect(error.code).toBe('ERROR')
  })
})
