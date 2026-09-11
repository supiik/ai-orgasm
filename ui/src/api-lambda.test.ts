import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('aws-amplify/auth', () => ({
  fetchAuthSession: vi.fn().mockResolvedValue({ tokens: { idToken: { toString: () => 'fake-id-token' } } }),
}))

// Regression coverage for the "first authenticated request right after a fresh sign-in can 401
// once even though the session is valid" bug (see git history: "retry once on 401 for the very
// first authenticated request"). A manual reload always fixed it in practice, confirming it was
// a timing race, not a real auth failure — authorizedFetch now retries once before giving up.
describe('authorizedFetch', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('does not retry a 401 when not authenticated — expected before the user has signed in, not an expired session', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    const { authorizedFetch } = await import('./api-lambda')
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()
    authStore.isAuthenticated = false

    const res = await authorizedFetch('https://example.test/list-playlists')

    expect(res.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(authStore.sessionExpired).toBe(false)
  })

  it('retries once and succeeds when the first 401 was transient', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const { authorizedFetch } = await import('./api-lambda')
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()
    authStore.isAuthenticated = true

    const resPromise = authorizedFetch('https://example.test/list-playlists')
    await vi.advanceTimersByTimeAsync(400)
    const res = await resPromise

    expect(res.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(authStore.sessionExpired).toBe(false)
  })

  it('flags sessionExpired when the retry also 401s — a genuinely expired session', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)

    const { authorizedFetch } = await import('./api-lambda')
    const { useAuthStore } = await import('@/stores/auth')
    const authStore = useAuthStore()
    authStore.isAuthenticated = true

    const resPromise = authorizedFetch('https://example.test/list-playlists')
    await vi.advanceTimersByTimeAsync(400)
    const res = await resPromise

    expect(res.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(authStore.sessionExpired).toBe(true)
  })
})
