import { describe, expect, it, vi } from 'vitest'
import { UpstreamError } from '../errors'
import { setLogSink } from '../logger'
import { SpotifyProvider, rankTracks } from './spotify'

setLogSink(() => {})

const creep = {
  id: 'spotify-creep',
  name: 'Creep',
  artists: [{ name: 'Radiohead' }],
  album: { name: 'Pablo Honey', release_date: '1993-02-22' },
  popularity: 85,
}

function tokenResponse() {
  return new Response(JSON.stringify({ access_token: 'token-1', expires_in: 3600 }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

function searchResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function provider(fetchFn: typeof fetch) {
  return new SpotifyProvider({ clientId: 'client-id', clientSecret: 'client-secret', fetchFn })
}

describe('SpotifyProvider', () => {
  it('requires clientId and clientSecret', () => {
    expect(() => new SpotifyProvider({ clientId: '', clientSecret: 'x' })).toThrow(/requires clientId and clientSecret/)
    expect(() => new SpotifyProvider({ clientId: 'x', clientSecret: '' })).toThrow(/requires clientId and clientSecret/)
  })

  it('fetches a client-credentials token, then searches with it as a bearer token', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(searchResponse(200, { tracks: { items: [creep] } }))

    const hits = await provider(fetchFn).search('radiohead creep', 10)

    const [tokenUrl, tokenInit] = fetchFn.mock.calls[0] as unknown as [string, RequestInit]
    expect(tokenUrl).toBe('https://accounts.spotify.com/api/token')
    expect(tokenInit.method).toBe('POST')
    expect((tokenInit.headers as Record<string, string>).Authorization).toBe(`Basic ${Buffer.from('client-id:client-secret').toString('base64')}`)

    const [searchUrl, searchInit] = fetchFn.mock.calls[1] as unknown as [string, RequestInit]
    const parsed = new URL(searchUrl)
    expect(parsed.origin + parsed.pathname).toBe('https://api.spotify.com/v1/search')
    expect(parsed.searchParams.get('type')).toBe('track')
    expect(parsed.searchParams.get('q')).toBe('radiohead creep')
    expect((searchInit.headers as Record<string, string>).Authorization).toBe('Bearer token-1')

    expect(hits).toEqual([
      { source: 'spotify', externalId: 'spotify-creep', artist: 'Radiohead', name: 'Creep', album: 'Pablo Honey', releaseYear: 1993, score: 85 },
    ])
  })

  it('reuses a cached token across searches instead of fetching a new one each time', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(searchResponse(200, { tracks: { items: [] } }))
      .mockResolvedValueOnce(searchResponse(200, { tracks: { items: [] } }))

    const p = provider(fetchFn)
    await p.search('a', 10)
    await p.search('b', 10)

    expect(fetchFn).toHaveBeenCalledTimes(3) // one token fetch + two searches
  })

  it('drops the cached token and retries once on a 401, then gives up on a second 401', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(searchResponse(401, {}))
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(searchResponse(200, { tracks: { items: [creep] } }))

    const hits = await provider(fetchFn).search('creep', 10)
    expect(fetchFn).toHaveBeenCalledTimes(4)
    expect(hits).toHaveLength(1)
  })

  it('wraps a token-endpoint failure in UpstreamError', async () => {
    const fetchFn = vi.fn().mockResolvedValueOnce(searchResponse(500, {}))
    await expect(provider(fetchFn).search('creep', 10)).rejects.toThrow(UpstreamError)
  })

  it('wraps a search-endpoint failure in UpstreamError', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(searchResponse(500, {}))
    await expect(provider(fetchFn).search('creep', 10)).rejects.toThrow(UpstreamError)
  })

  it('wraps a network failure in UpstreamError', async () => {
    const fetchFn = vi.fn().mockRejectedValueOnce(new Error('network down'))
    await expect(provider(fetchFn).search('creep', 10)).rejects.toThrow(UpstreamError)
  })

  it('tolerates an empty response body', async () => {
    const fetchFn = vi.fn()
      .mockResolvedValueOnce(tokenResponse())
      .mockResolvedValueOnce(searchResponse(200, {}))
    expect(await provider(fetchFn).search('creep', 10)).toEqual([])
  })
})

describe('rankTracks', () => {
  it('keeps the most popular instance of each artist+title group, sorted by popularity', () => {
    const bootleg = { ...creep, id: 'bootleg', popularity: 10 }
    const live = { ...creep, id: 'live', name: 'Creep (Live)', popularity: 40 }
    const tracks = rankTracks([bootleg, creep, live])
    expect(tracks.map((t) => t.id)).toEqual(['spotify-creep', 'live'])
  })

  it('is case/punctuation-insensitive when grouping', () => {
    const variant = { ...creep, id: 'variant', name: 'CREEP!!', popularity: 5 }
    expect(rankTracks([creep, variant]).map((t) => t.id)).toEqual(['spotify-creep'])
  })
})
