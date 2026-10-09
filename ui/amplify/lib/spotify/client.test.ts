import { describe, expect, it, vi } from 'vitest'
import { UpstreamError } from '../errors'
import { setLogSink } from '../logger'
import { SpotifyClient, SpotifyNotFoundError, SpotifyUnauthorizedError } from './client'

setLogSink(() => {})

function fetchReturning(status: number, body?: unknown) {
  return vi.fn(async () => new Response(body === undefined ? undefined : JSON.stringify(body), { status }))
}

function client(fetchFn: typeof fetch) {
  return new SpotifyClient({ clientId: 'client-id', clientSecret: 'client-secret', fetchFn })
}

describe('SpotifyClient', () => {
  it('exchangeCode posts a form with Basic auth and parses the tokens', async () => {
    const fetchFn = fetchReturning(200, { access_token: 'access-1', refresh_token: 'refresh-1', expires_in: 3600 })

    const tokens = await client(fetchFn).exchangeCode('code-1', 'https://ui.example/callback')

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('https://accounts.spotify.com/api/token')
    expect(init.method).toBe('POST')
    const headers = init.headers as Record<string, string>
    expect(headers.Authorization).toBe(`Basic ${Buffer.from('client-id:client-secret').toString('base64')}`)
    expect(headers['Content-Type']).toBe('application/x-www-form-urlencoded')
    expect((init.body as URLSearchParams).get('code')).toBe('code-1')
    expect((init.body as URLSearchParams).get('grant_type')).toBe('authorization_code')
    expect(tokens.accessToken).toBe('access-1')
    expect(tokens.refreshToken).toBe('refresh-1')
    expect(new Date(tokens.expiresAt).getTime()).toBeGreaterThan(Date.now())
  })

  it('refreshToken returns an empty refreshToken when Spotify omits a rotated one', async () => {
    const fetchFn = fetchReturning(200, { access_token: 'access-2', expires_in: 3600 })
    const tokens = await client(fetchFn).refreshToken('refresh-1')
    expect(tokens.accessToken).toBe('access-2')
    expect(tokens.refreshToken).toBe('')
  })

  it('getCurrentUser parses id and display name', async () => {
    const fetchFn = fetchReturning(200, { id: 'spotify-user-1', display_name: 'Thom' })
    const user = await client(fetchFn).getCurrentUser('access-1')
    expect(user).toEqual({ id: 'spotify-user-1', displayName: 'Thom' })
  })

  it('searchTrackUri returns the first match, sending a bearer token', async () => {
    const fetchFn = fetchReturning(200, { tracks: { items: [{ uri: 'spotify:track:1' }] } })

    const uri = await client(fetchFn).searchTrackUri('access-1', 'Radiohead', 'Creep')

    expect(uri).toBe('spotify:track:1')
    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toContain('https://api.spotify.com/v1/search?')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer access-1')
    const parsed = new URL(url)
    expect(parsed.searchParams.get('q')).toBe('artist:"Radiohead" track:"Creep"')
    expect(parsed.searchParams.get('type')).toBe('track')
  })

  it('searchTrackUri returns undefined when there are no results', async () => {
    const fetchFn = fetchReturning(200, { tracks: { items: [] } })
    const uri = await client(fetchFn).searchTrackUri('access-1', 'Unknown', 'Nothing')
    expect(uri).toBeUndefined()
  })

  it('searchTrackUri strips quote characters from the artist/title before building the query', async () => {
    const fetchFn = fetchReturning(200, { tracks: { items: [] } })
    await client(fetchFn).searchTrackUri('access-1', '"Weird" Al', 'Foo "Bar"')
    const [url] = fetchFn.mock.calls[0] as unknown as [string]
    expect(new URL(url).searchParams.get('q')).toBe('artist:"Weird Al" track:"Foo Bar"')
  })

  it('createPlaylist posts name/description and parses id + external url', async () => {
    const fetchFn = fetchReturning(200, { id: 'pl-1', external_urls: { spotify: 'https://open.spotify.com/playlist/pl-1' } })

    const playlist = await client(fetchFn).createPlaylist('access-1', 'spotify-user-1', 'Mix', 'desc')

    expect(playlist).toEqual({ id: 'pl-1', url: 'https://open.spotify.com/playlist/pl-1' })
    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('https://api.spotify.com/v1/users/spotify-user-1/playlists')
    expect(JSON.parse(init.body as string)).toEqual({ name: 'Mix', description: 'desc', public: false })
  })

  it('replacePlaylistTracks PUTs the track uris', async () => {
    const fetchFn = fetchReturning(200, {})

    await client(fetchFn).replacePlaylistTracks('access-1', 'pl-1', ['spotify:track:1'])

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('https://api.spotify.com/v1/playlists/pl-1/tracks')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({ uris: ['spotify:track:1'] })
  })

  it('maps a 401 to SpotifyUnauthorizedError', async () => {
    const fetchFn = fetchReturning(401)
    await expect(client(fetchFn).getCurrentUser('bad-token')).rejects.toBeInstanceOf(SpotifyUnauthorizedError)
  })

  it('maps a 404 to SpotifyNotFoundError', async () => {
    const fetchFn = fetchReturning(404)
    await expect(client(fetchFn).replacePlaylistTracks('access-1', 'missing', [])).rejects.toBeInstanceOf(SpotifyNotFoundError)
  })

  it('maps any other failure status to UpstreamError', async () => {
    const fetchFn = fetchReturning(500)
    await expect(client(fetchFn).getCurrentUser('access-1')).rejects.toBeInstanceOf(UpstreamError)
  })

  it('maps a network failure to UpstreamError', async () => {
    const fetchFn = vi.fn(async () => {
      throw new TypeError('network down')
    })
    await expect(client(fetchFn).getCurrentUser('access-1')).rejects.toBeInstanceOf(UpstreamError)
  })
})
