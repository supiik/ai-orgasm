import { UpstreamError } from '../errors'
import { log } from '../logger'
import type { SongSearchHit, SongSearchProvider } from './provider'

/**
 * Spotify's Search API (https://developer.spotify.com/documentation/web-api/reference/search)
 * as an alternative to `musicbrainz.ts`. Every track Spotify returns carries a `popularity`
 * (0–100) — exactly the signal MusicBrainz's recording index lacks, which is why a title-only
 * MusicBrainz query is a coin toss among hundreds of equally-scored covers/live takes. Here,
 * ranking the relevance-ordered result set by popularity surfaces the canonical studio
 * recording over bootlegs/covers/remixes.
 *
 * Authenticates via the Client Credentials flow (app-only, no user login) — a different token
 * from the per-contributor OAuth connection `lib/spotify/client.ts` manages for playlist
 * export; this one only proves the app's own identity to call a public endpoint, so it's
 * cached module-level (via the instance field below) rather than per-request — a warm Lambda
 * reuses it until shortly before Spotify's own ~1 h expiry.
 */

export interface SpotifyProviderOptions {
  clientId: string
  clientSecret: string
  timeoutMs?: number
  /** Tracks fetched per search before dedupe/ranking (Spotify caps at 50). */
  fetchLimit?: number
  /** Injectable for tests. */
  fetchFn?: typeof fetch
}

interface Track {
  id: string
  name: string
  artists?: { name: string }[]
  album?: { name?: string; release_date?: string }
  popularity?: number
}

interface SearchResponse {
  tracks?: { items?: Track[] }
}

interface TokenResponse {
  access_token: string
  expires_in: number
}

const ACCOUNTS_TOKEN_URL = 'https://accounts.spotify.com/api/token'
const API_BASE = 'https://api.spotify.com/v1'

/** Refresh this long before Spotify's stated expiry so a request never races an about-to-expire token. */
const EXPIRY_SAFETY_MARGIN_MS = 60_000

export class SpotifyProvider implements SongSearchProvider {
  readonly name = 'spotify'
  private readonly clientId: string
  private readonly clientSecret: string
  private readonly timeoutMs: number
  private readonly fetchLimit: number
  private readonly fetchFn: typeof fetch
  private cachedToken?: { accessToken: string; expiresAt: number }

  constructor(options: SpotifyProviderOptions) {
    if (!options.clientId?.trim() || !options.clientSecret?.trim()) {
      throw new Error('SpotifyProvider requires clientId and clientSecret')
    }
    this.clientId = options.clientId
    this.clientSecret = options.clientSecret
    this.timeoutMs = options.timeoutMs ?? 8000
    this.fetchLimit = Math.min(options.fetchLimit ?? 50, 50)
    this.fetchFn = options.fetchFn ?? fetch
  }

  async search(query: string, limit: number): Promise<SongSearchHit[]> {
    const params = new URLSearchParams({ q: query, type: 'track', limit: String(this.fetchLimit) })
    const body = await this.get<SearchResponse>(`${API_BASE}/search?${params}`)
    return rankTracks(body.tracks?.items ?? [])
      .slice(0, limit)
      .map(toHit)
  }

  private async getAccessToken(): Promise<string> {
    if (this.cachedToken && this.cachedToken.expiresAt > Date.now()) return this.cachedToken.accessToken
    const credentials = Buffer.from(`${this.clientId}:${this.clientSecret}`).toString('base64')
    let res: Response
    try {
      res = await this.fetchFn(ACCOUNTS_TOKEN_URL, {
        method: 'POST',
        headers: { Authorization: `Basic ${credentials}`, 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ grant_type: 'client_credentials' }),
        signal: AbortSignal.timeout(this.timeoutMs),
      })
    } catch (e) {
      log.warn('Spotify token request failed', { 'error.type': (e as Error).name, 'error.message': (e as Error).message })
      throw new UpstreamError('Song search is temporarily unavailable')
    }
    if (!res.ok) {
      log.warn('Spotify token request responded with an error', { 'http.response.status_code': res.status })
      throw new UpstreamError('Song search is temporarily unavailable')
    }
    const body = (await res.json()) as TokenResponse
    this.cachedToken = { accessToken: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 - EXPIRY_SAFETY_MARGIN_MS }
    return this.cachedToken.accessToken
  }

  /** `retried` guards against looping forever if Spotify keeps rejecting a freshly-minted token. */
  private async get<T>(url: string, retried = false): Promise<T> {
    const accessToken = await this.getAccessToken()
    let res: Response
    try {
      res = await this.fetchFn(url, {
        headers: { Authorization: `Bearer ${accessToken}` },
        signal: AbortSignal.timeout(this.timeoutMs),
      })
    } catch (e) {
      log.warn('Spotify search request failed', { 'error.type': (e as Error).name, 'error.message': (e as Error).message })
      throw new UpstreamError('Song search is temporarily unavailable')
    }
    if (res.status === 401 && !retried) {
      this.cachedToken = undefined
      return this.get(url, true)
    }
    if (!res.ok) {
      log.warn('Spotify responded with an error', { 'http.response.status_code': res.status })
      throw new UpstreamError('Song search is temporarily unavailable')
    }
    return (await res.json()) as T
  }
}

/**
 * Groups tracks that are the same song (case/punctuation-insensitive artist + title), keeping
 * the most popular instance of each, then orders groups by that popularity — the studio
 * original and well-known covers naturally outrank bootlegs/live takes/remixes of the same
 * title. Exported for tests.
 */
export function rankTracks(tracks: Track[]): Track[] {
  const groups = new Map<string, Track>()
  for (const t of tracks) {
    const key = `${normalise(primaryArtist(t))}|${normalise(t.name)}`
    const existing = groups.get(key)
    if (!existing || (t.popularity ?? 0) > (existing.popularity ?? 0)) groups.set(key, t)
  }
  return [...groups.values()].sort((a, b) => (b.popularity ?? 0) - (a.popularity ?? 0))
}

function toHit(t: Track): SongSearchHit {
  return {
    source: 'spotify',
    externalId: t.id,
    artist: primaryArtist(t),
    name: t.name,
    album: t.album?.name,
    releaseYear: yearOf(t.album?.release_date),
    score: t.popularity,
  }
}

/** Joins featured artists the way they'd be typed in the song form: "Daft Punk, Pharrell Williams". */
function primaryArtist(t: Track): string {
  return (t.artists ?? []).map((a) => a.name).join(', ')
}

/** Spotify album release dates are `YYYY`, `YYYY-MM` or `YYYY-MM-DD` depending on precision. */
function yearOf(date: string | undefined): number | undefined {
  const m = date?.match(/^(\d{4})/)
  return m ? Number(m[1]) : undefined
}

function normalise(s: string): string {
  return s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '')
}
