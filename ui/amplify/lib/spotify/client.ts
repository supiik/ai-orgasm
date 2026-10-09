import { UpstreamError } from '../errors'
import { log } from '../logger'

/**
 * Thin wrapper around Spotify's Accounts + Web API. Mirrors `lib/songSearch/musicbrainz.ts`'s
 * conventions (injectable `fetchFn` for tests, `AbortSignal.timeout`, `UpstreamError` on
 * failure, `log.warn` on errors) rather than pulling in an HTTP client library. Never logs a
 * token — only status codes and error names/messages.
 */

export interface SpotifyTokens {
  accessToken: string
  /** Empty string when Spotify's refresh response omitted a rotated refresh token — the caller
   * (lib/services/spotify.ts) keeps using the previous one in that case, never overwriting with empty. */
  refreshToken: string
  /** ISO-8601 instant. */
  expiresAt: string
}

export interface SpotifyUser {
  id: string
  displayName?: string
}

export interface SpotifyPlaylist {
  id: string
  url: string
}

/** 401 from Spotify — caught by `lib/services/spotify.ts`'s token-refresh wrapper to retry once. */
export class SpotifyUnauthorizedError extends UpstreamError {
  constructor() {
    super('Spotify rejected the access token')
    this.name = 'SpotifyUnauthorizedError'
  }
}

/** 404 from Spotify — used to detect a stale `spotifyPlaylistId` (e.g. deleted directly in
 * Spotify) and fall back to creating a fresh playlist. */
export class SpotifyNotFoundError extends UpstreamError {
  constructor() {
    super('Spotify resource not found')
    this.name = 'SpotifyNotFoundError'
  }
}

export interface SpotifyClientOptions {
  clientId: string
  clientSecret: string
  timeoutMs?: number
  /** Injectable for tests. */
  fetchFn?: typeof fetch
}

const ACCOUNTS_TOKEN_URL = 'https://accounts.spotify.com/api/token'
const API_BASE = 'https://api.spotify.com/v1'

export class SpotifyClient {
  private readonly clientId: string
  private readonly clientSecret: string
  private readonly timeoutMs: number
  private readonly fetchFn: typeof fetch

  constructor(options: SpotifyClientOptions) {
    this.clientId = options.clientId
    this.clientSecret = options.clientSecret
    this.timeoutMs = options.timeoutMs ?? 8000
    this.fetchFn = options.fetchFn ?? fetch
  }

  exchangeCode(code: string, redirectUri: string): Promise<SpotifyTokens> {
    return this.requestToken({ grant_type: 'authorization_code', code, redirect_uri: redirectUri })
  }

  refreshToken(refreshToken: string): Promise<SpotifyTokens> {
    return this.requestToken({ grant_type: 'refresh_token', refresh_token: refreshToken })
  }

  async getCurrentUser(accessToken: string): Promise<SpotifyUser> {
    const body = await this.request<{ id: string; display_name?: string }>('GET', `${API_BASE}/me`, accessToken)
    return { id: body.id, displayName: body.display_name }
  }

  async searchTrackUri(accessToken: string, artist: string, title: string): Promise<string | undefined> {
    const query = `artist:"${sanitize(artist)}" track:"${sanitize(title)}"`
    const params = new URLSearchParams({ q: query, type: 'track', limit: '1' })
    const body = await this.request<{ tracks?: { items?: { uri: string }[] } }>(
      'GET',
      `${API_BASE}/search?${params}`,
      accessToken,
    )
    return body.tracks?.items?.[0]?.uri
  }

  async createPlaylist(
    accessToken: string,
    spotifyUserId: string,
    name: string,
    description: string,
  ): Promise<SpotifyPlaylist> {
    const body = await this.request<{ id: string; external_urls: { spotify: string } }>(
      'POST',
      `${API_BASE}/users/${encodeURIComponent(spotifyUserId)}/playlists`,
      accessToken,
      { name, description, public: false },
    )
    return { id: body.id, url: body.external_urls.spotify }
  }

  async replacePlaylistTracks(accessToken: string, playlistId: string, trackUris: string[]): Promise<void> {
    await this.request(
      'PUT',
      `${API_BASE}/playlists/${encodeURIComponent(playlistId)}/tracks`,
      accessToken,
      { uris: trackUris },
    )
  }

  private async requestToken(form: Record<string, string>): Promise<SpotifyTokens> {
    const credentials = Buffer.from(`${this.clientId}:${this.clientSecret}`).toString('base64')
    let res: Response
    try {
      res = await this.fetchFn(ACCOUNTS_TOKEN_URL, {
        method: 'POST',
        headers: { Authorization: `Basic ${credentials}`, 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams(form),
        signal: AbortSignal.timeout(this.timeoutMs),
      })
    } catch (e) {
      log.warn('Spotify token request failed', { 'error.type': (e as Error).name, 'error.message': (e as Error).message })
      throw new UpstreamError('Spotify is temporarily unavailable')
    }
    await this.assertOk(res)
    const body = (await res.json()) as { access_token: string; refresh_token?: string; expires_in: number }
    return {
      accessToken: body.access_token,
      refreshToken: body.refresh_token ?? '',
      expiresAt: new Date(Date.now() + body.expires_in * 1000).toISOString(),
    }
  }

  private async request<T>(method: string, url: string, accessToken: string, jsonBody?: unknown): Promise<T> {
    let res: Response
    try {
      res = await this.fetchFn(url, {
        method,
        headers: {
          Authorization: `Bearer ${accessToken}`,
          ...(jsonBody !== undefined ? { 'Content-Type': 'application/json' } : {}),
        },
        body: jsonBody !== undefined ? JSON.stringify(jsonBody) : undefined,
        signal: AbortSignal.timeout(this.timeoutMs),
      })
    } catch (e) {
      log.warn('Spotify API call failed', { 'error.type': (e as Error).name, 'error.message': (e as Error).message })
      throw new UpstreamError('Spotify is temporarily unavailable')
    }
    await this.assertOk(res)
    if (res.status === 204) return undefined as T
    const text = await res.text()
    return (text ? JSON.parse(text) : undefined) as T
  }

  private async assertOk(res: Response): Promise<void> {
    if (res.ok) return
    if (res.status === 401) throw new SpotifyUnauthorizedError()
    if (res.status === 404) throw new SpotifyNotFoundError()
    log.warn('Spotify responded with an error', { 'http.response.status_code': res.status })
    throw new UpstreamError('Spotify is temporarily unavailable')
  }
}

function sanitize(value: string): string {
  return value.replace(/"/g, '')
}
