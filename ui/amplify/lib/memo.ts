/**
 * Caches an async lookup by key for the lifetime of one call chain — i.e. one request.
 *
 * The read paths here hydrate related names row by row ("lookup-at-read", see CLAUDE.md "No JOIN
 * FETCH equivalent"), so a list of N rows issued N GetItems even when they all pointed at the
 * same few contributors or playlists. Wrapping the lookup collapses those to one per distinct id.
 * Deliberately request-scoped: a longer-lived cache would serve stale rows across invocations,
 * since Lambda reuses execution environments.
 */
export function memoize<V>(fn: (key: bigint) => Promise<V>): (key: bigint) => Promise<V> {
  const cache = new Map<bigint, Promise<V>>()
  return (key) => {
    const hit = cache.get(key)
    if (hit) return hit
    const pending = fn(key)
    cache.set(key, pending)
    return pending
  }
}
