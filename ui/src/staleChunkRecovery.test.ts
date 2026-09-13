// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import {
  installStaleChunkRecovery,
  isStaleChunkError,
  RELOAD_MARKER_KEY,
  RELOAD_WINDOW_MS,
} from './staleChunkRecovery'

const chunkError = new TypeError(
  'Failed to fetch dynamically imported module: https://example.test/assets/SongsView-old.js',
)

function memoryStorage(): Pick<Storage, 'getItem' | 'setItem'> & { map: Map<string, string> } {
  const map = new Map<string, string>()
  return {
    map,
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => void map.set(k, v),
  }
}

function makeRouter(componentError: unknown = chunkError): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div>home</div>' } },
      { path: '/songs', component: () => Promise.reject(componentError) },
    ],
  })
}

describe('isStaleChunkError', () => {
  it.each([
    'Failed to fetch dynamically imported module: x',
    'error loading dynamically imported module: x',
    'Importing a module script failed.',
    'Unable to preload CSS for /assets/x.css',
  ])('recognises "%s"', (message) => {
    expect(isStaleChunkError(new Error(message))).toBe(true)
  })

  it('ignores unrelated errors and non-Error values', () => {
    expect(isStaleChunkError(new Error('Network Error'))).toBe(false)
    expect(isStaleChunkError('Failed to fetch dynamically imported module')).toBe(true)
    expect(isStaleChunkError(undefined)).toBe(false)
  })
})

describe('installStaleChunkRecovery', () => {
  let clock: number
  const now = () => clock

  beforeEach(() => {
    clock = 1_000_000
  })

  it('does a full navigation to the target route when its chunk fails to load', async () => {
    const router = makeRouter()
    const navigate = vi.fn()
    const storage = memoryStorage()
    installStaleChunkRecovery(router, { storage, navigate, now })

    await router.push('/songs?page=2').catch(() => undefined)

    expect(navigate).toHaveBeenCalledWith('/songs?page=2')
    expect(storage.map.get(RELOAD_MARKER_KEY)).toBe(String(clock))
  })

  it('reloads only once within the window, so a genuinely missing chunk cannot loop', async () => {
    const router = makeRouter()
    const navigate = vi.fn()
    const storage = memoryStorage()
    installStaleChunkRecovery(router, { storage, navigate, now })

    await router.push('/songs').catch(() => undefined)
    clock += RELOAD_WINDOW_MS - 1
    await router.push('/songs').catch(() => undefined)
    expect(navigate).toHaveBeenCalledTimes(1)

    clock += 1
    await router.push('/songs').catch(() => undefined)
    expect(navigate).toHaveBeenCalledTimes(2)
  })

  it('leaves unrelated navigation errors alone', async () => {
    const router = makeRouter(new Error('Network Error'))
    const navigate = vi.fn()
    installStaleChunkRecovery(router, { storage: memoryStorage(), navigate, now })

    await router.push('/songs').catch(() => undefined)

    expect(navigate).not.toHaveBeenCalled()
  })

  it('still reloads when storage throws (private mode)', async () => {
    const router = makeRouter()
    const navigate = vi.fn()
    const throwing: Pick<Storage, 'getItem' | 'setItem'> = {
      getItem: () => {
        throw new DOMException('blocked', 'SecurityError')
      },
      setItem: () => {
        throw new DOMException('blocked', 'SecurityError')
      },
    }
    installStaleChunkRecovery(router, { storage: throwing, navigate, now })

    await router.push('/songs').catch(() => undefined)

    expect(navigate).toHaveBeenCalledWith('/songs')
  })

  it("handles Vite's vite:preloadError for the current location and swallows the event", () => {
    const router = makeRouter()
    const navigate = vi.fn()
    installStaleChunkRecovery(router, { storage: memoryStorage(), navigate, now })

    const event = new Event('vite:preloadError', { cancelable: true })
    window.dispatchEvent(event)

    expect(navigate).toHaveBeenCalledWith(window.location.pathname + window.location.search + window.location.hash)
    expect(event.defaultPrevented).toBe(true)
  })
})
