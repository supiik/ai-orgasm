import { ValidationError } from '../errors'
import { createSongSearchProvider, type SongSearchHit, type SongSearchProvider } from '../songSearch'

export const MIN_QUERY_LENGTH = 2
export const MAX_QUERY_LENGTH = 200
export const DEFAULT_LIMIT = 10
export const MAX_LIMIT = 25

let defaultProvider: SongSearchProvider | undefined

/**
 * Looks a free-text query up in the configured external catalogue. The provider instance is
 * module-level on purpose (it holds configuration only, no per-request state) so a warm Lambda
 * doesn't rebuild it on every call; results are never cached across invocations.
 */
export async function searchSongs(
  query: string | undefined,
  limit: number | undefined,
  provider: SongSearchProvider = (defaultProvider ??= createSongSearchProvider()),
): Promise<SongSearchHit[]> {
  const q = query?.trim() ?? ''
  const errors: string[] = []
  if (q.length < MIN_QUERY_LENGTH) errors.push(`q must be at least ${MIN_QUERY_LENGTH} characters`)
  if (q.length > MAX_QUERY_LENGTH) errors.push(`q must be at most ${MAX_QUERY_LENGTH} characters`)
  if (limit !== undefined && (!Number.isInteger(limit) || limit < 1)) errors.push('limit must be a positive integer')
  if (errors.length) throw new ValidationError(errors)

  return provider.search(q, Math.min(limit ?? DEFAULT_LIMIT, MAX_LIMIT))
}
