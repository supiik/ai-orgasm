import type { Router } from 'vue-router'

/**
 * Recovers from the "stale deployment" failure mode of a content-hashed SPA.
 *
 * Every route except Home is lazy-loaded (`() => import('../views/X.vue')`), and Vite names the
 * resulting chunks by content hash. A tab that loaded the app *before* a deploy still holds the
 * old main bundle, whose dynamic imports point at chunk files the new deploy removed — so the
 * first click on a not-yet-visited route fails with "Failed to fetch dynamically imported
 * module" and the page just sits there until the user hard-refreshes. Amplify Hosting keeps
 * only the latest deployment, so nothing on the server side can keep the old chunks alive.
 *
 * The fix is the one Vite and Vue Router both document: on a chunk-load error, do a full
 * navigation to the route the user wanted, which fetches the new `index.html` and its chunks.
 * A timestamp in sessionStorage keeps this to one attempt per {@link RELOAD_WINDOW_MS}, so a
 * chunk that is genuinely broken (or a flaky network) surfaces the error instead of looping.
 */

export const RELOAD_MARKER_KEY = 'orgasm:stale-chunk-reload'
export const RELOAD_WINDOW_MS = 10_000

const STALE_CHUNK_MESSAGES = [
  'Failed to fetch dynamically imported module', // Chromium
  'error loading dynamically imported module', // Firefox
  'Importing a module script failed', // Safari
  'Unable to preload CSS', // Vite's own preload helper
]

export function isStaleChunkError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error)
  return STALE_CHUNK_MESSAGES.some((needle) => message.includes(needle))
}

export interface StaleChunkRecoveryOptions {
  /** Where the "already reloaded" marker lives; defaults to `sessionStorage`. */
  storage?: Pick<Storage, 'getItem' | 'setItem'>
  /** Performs the full navigation; defaults to `window.location.assign`. */
  navigate?: (path: string) => void
  /** Clock, for tests. */
  now?: () => number
}

export function installStaleChunkRecovery(router: Router, options: StaleChunkRecoveryOptions = {}): void {
  const storage = options.storage ?? window.sessionStorage
  const navigate = options.navigate ?? ((path: string) => window.location.assign(path))
  const now = options.now ?? Date.now

  const reloadOnce = (targetPath: string): boolean => {
    // Storage access can throw (private mode, blocked site data); treat that as "no marker".
    let last: number | null = null
    try {
      const raw = storage.getItem(RELOAD_MARKER_KEY)
      last = raw ? Number(raw) : null
    } catch {
      last = null
    }
    if (last !== null && now() - last < RELOAD_WINDOW_MS) return false
    try {
      storage.setItem(RELOAD_MARKER_KEY, String(now()))
    } catch {
      // Without a marker we still reload — a loop is bounded by the user's patience, not ours,
      // but that beats never recovering.
    }
    navigate(targetPath)
    return true
  }

  // The dynamic import of a route component rejected → the navigation failed with that error.
  router.onError((error, to) => {
    if (isStaleChunkError(error)) reloadOnce(to.fullPath)
  })

  // A chunk's *dependency* (another JS chunk or its CSS) failed to preload. Vite fires this
  // before throwing; preventDefault() swallows the throw once we've decided to reload.
  window.addEventListener('vite:preloadError', (event) => {
    const { pathname, search, hash } = window.location
    if (reloadOnce(pathname + search + hash)) event.preventDefault()
  })
}
