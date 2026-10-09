import { MusicBrainzProvider } from './musicbrainz'
import { SpotifyProvider } from './spotify'
import type { SongSearchProvider } from './provider'

export type { SongSearchHit, SongSearchProvider } from './provider'

/**
 * Registry of available providers, keyed by `SONG_SEARCH_PROVIDER` (default `musicbrainz`).
 * Each factory reads its own configuration from the environment (all injected by
 * `backend.ts`), so switching catalogues is a deploy-time setting, not a code change. The
 * `spotify` entry reuses the same `SPOTIFY_CLIENT_ID`/`SPOTIFY_CLIENT_SECRET` already set for
 * the Spotify playlist-export feature (via the Client Credentials flow, not the per-contributor
 * OAuth connection) — see `lib/songSearch/spotify.ts`.
 */
const factories: Record<string, () => SongSearchProvider> = {
  musicbrainz: () =>
    new MusicBrainzProvider({
      userAgent: process.env.SONG_SEARCH_USER_AGENT ?? 'orgasm/1.0 (https://github.com/supiik/ai-orgasm)',
      baseUrl: process.env.MUSICBRAINZ_BASE_URL,
    }),
  spotify: () =>
    new SpotifyProvider({
      clientId: process.env.SPOTIFY_CLIENT_ID ?? '',
      clientSecret: process.env.SPOTIFY_CLIENT_SECRET ?? '',
    }),
}

export function createSongSearchProvider(name = process.env.SONG_SEARCH_PROVIDER ?? 'musicbrainz'): SongSearchProvider {
  const factory = factories[name]
  if (!factory) throw new Error(`Unknown song search provider "${name}" (known: ${Object.keys(factories).join(', ')})`)
  return factory()
}
