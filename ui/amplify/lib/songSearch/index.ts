import { MusicBrainzProvider } from './musicbrainz'
import type { SongSearchProvider } from './provider'

export type { SongSearchHit, SongSearchProvider } from './provider'

/**
 * Registry of available providers, keyed by `SONG_SEARCH_PROVIDER` (default `musicbrainz`).
 * Each factory reads its own configuration from the environment (all injected by
 * `backend.ts`), so switching catalogues is a deploy-time setting, not a code change.
 */
const factories: Record<string, () => SongSearchProvider> = {
  musicbrainz: () =>
    new MusicBrainzProvider({
      userAgent: process.env.SONG_SEARCH_USER_AGENT ?? 'orgasm/1.0 (https://github.com/supiik/ai-orgasm)',
      baseUrl: process.env.MUSICBRAINZ_BASE_URL,
    }),
}

export function createSongSearchProvider(name = process.env.SONG_SEARCH_PROVIDER ?? 'musicbrainz'): SongSearchProvider {
  const factory = factories[name]
  if (!factory) throw new Error(`Unknown song search provider "${name}" (known: ${Object.keys(factories).join(', ')})`)
  return factory()
}
