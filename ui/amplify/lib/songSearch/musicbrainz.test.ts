import { describe, expect, it, vi } from 'vitest'
import { UpstreamError } from '../errors'
import { setLogSink } from '../logger'
import { MusicBrainzProvider, buildLuceneQuery, isVariantTitle, pickRelease, rankRecordings, yearOf } from './musicbrainz'

setLogSink(() => {})

const creep = {
  id: 'mbid-creep',
  score: 100,
  title: 'Creep',
  'first-release-date': '1992-09-21',
  'artist-credit': [{ name: 'Radiohead' }],
  releases: [
    { title: 'Radiohead: The Best Of', date: '2008-06-02', status: 'Official', 'release-group': { 'primary-type': 'Album', 'secondary-types': ['Compilation'] } },
    { title: 'Creep', date: '1992-09-21', status: 'Official', 'release-group': { 'primary-type': 'Single' } },
    { title: 'Pablo Honey', date: '1993-02-22', status: 'Official', 'release-group': { 'primary-type': 'Album' } },
    { title: 'Pablo Honey (Collector\'s Edition)', date: '2009-03-24', status: 'Official', 'release-group': { 'primary-type': 'Album' } },
  ],
}

function fetchReturning(status: number, body: unknown) {
  return vi.fn(async () => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))
}

function provider(fetchFn: typeof fetch, fetchLimit = 100) {
  return new MusicBrainzProvider({ userAgent: 'orgasm-test/1.0 (test@example.com)', baseUrl: 'https://mb.test/ws/2/', fetchFn, fetchLimit })
}

describe('MusicBrainzProvider', () => {
  it('sends the mandatory User-Agent and a JSON recording search', async () => {
    const fetchFn = fetchReturning(200, { recordings: [] })
    await provider(fetchFn).search('radiohead creep', 7)

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit]
    const parsed = new URL(url)
    expect(parsed.origin + parsed.pathname).toBe('https://mb.test/ws/2/recording')
    expect(parsed.searchParams.get('fmt')).toBe('json')
    // Fetches a larger page than asked for — dedupe/ranking happens on our side.
    expect(parsed.searchParams.get('limit')).toBe('100')
    expect(parsed.searchParams.get('query')).toBe(
      '+((recording:radiohead OR artist:radiohead) AND (recording:creep OR artist:creep)) +status:official '
      + '(artist:radiohead AND recording:creep)^3 (recording:radiohead AND artist:creep)^3 '
      + '(recording:radiohead AND recording:creep)^2',
    )
    expect((init.headers as Record<string, string>)['User-Agent']).toBe('orgasm-test/1.0 (test@example.com)')
    expect(init.signal).toBeInstanceOf(AbortSignal)
  })

  it('maps a recording to a hit, choosing the original studio album over singles/compilations/reissues', async () => {
    const hits = await provider(fetchReturning(200, { recordings: [creep] })).search('creep', 10)

    expect(hits).toEqual([
      { source: 'musicbrainz', externalId: 'mbid-creep', artist: 'Radiohead', name: 'Creep', album: 'Pablo Honey', releaseYear: 1992, score: 100 },
    ])
  })

  it('joins multi-artist credits with their join phrases', async () => {
    const recording = { ...creep, 'artist-credit': [{ name: 'Simon', joinphrase: ' & ' }, { name: 'Garfunkel' }] }
    const [hit] = await provider(fetchReturning(200, { recordings: [recording] })).search('sound of silence', 10)
    expect(hit.artist).toBe('Simon & Garfunkel')
  })

  it('falls back to the first-release-date and no album when the recording has no releases', async () => {
    const recording = { ...creep, releases: undefined }
    const [hit] = await provider(fetchReturning(200, { recordings: [recording] })).search('creep', 10)
    expect(hit.album).toBeUndefined()
    expect(hit.releaseYear).toBe(1992)
  })

  it('reports the earliest known year even when the chosen album is a later reissue', async () => {
    const recording = { ...creep, 'first-release-date': '1991-09-24', releases: [{ title: 'Nevermind', date: '2011-09-26', status: 'Official', 'release-group': { 'primary-type': 'Album' } }] }
    const [hit] = await provider(fetchReturning(200, { recordings: [recording] })).search('teen spirit', 10)
    expect(hit).toMatchObject({ album: 'Nevermind', releaseYear: 1991 })
  })

  it('tolerates an empty response body', async () => {
    expect(await provider(fetchReturning(200, {})).search('creep', 10)).toEqual([])
  })

  it('throws UpstreamError on a non-2xx response', async () => {
    await expect(provider(fetchReturning(500, {})).search('creep', 10)).rejects.toThrow('temporarily unavailable')
    await expect(provider(fetchReturning(404, {})).search('creep', 10)).rejects.toBeInstanceOf(UpstreamError)
  })

  it('retries once on 503 "server busy", then gives up', async () => {
    vi.useFakeTimers()
    try {
      const busy = () => new Response('{"error":"busy"}', { status: 503, headers: { 'Retry-After': '2' } })
      const fetchFn = vi.fn().mockResolvedValueOnce(busy()).mockResolvedValueOnce(new Response(JSON.stringify({ recordings: [creep] }), { status: 200 }))
      const pending = provider(fetchFn as unknown as typeof fetch).search('creep', 10)
      await vi.runAllTimersAsync()
      expect((await pending).map((h) => h.name)).toEqual(['Creep'])
      expect(fetchFn).toHaveBeenCalledTimes(2)
      expect(vi.getTimerCount()).toBe(0)

      const alwaysBusy = vi.fn(async () => busy())
      const failing = provider(alwaysBusy as unknown as typeof fetch).search('creep', 10).catch((e) => e)
      await vi.runAllTimersAsync()
      expect(await failing).toBeInstanceOf(UpstreamError)
      expect(await failing).toMatchObject({ message: 'Song search is busy, try again shortly' })
      expect(alwaysBusy).toHaveBeenCalledTimes(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('dedupes recordings of the same song and honours the requested limit', async () => {
    const live = { ...creep, id: 'mbid-live', title: 'creep', releases: [{ title: 'Live at Glastonbury', date: '1997', status: 'Official', 'release-group': { 'primary-type': 'Album', 'secondary-types': ['Live'] } }] }
    const cover = { ...creep, id: 'mbid-cover', 'artist-credit': [{ name: 'Someone Else' }], releases: [creep.releases[1]] }
    const hits = await provider(fetchReturning(200, { recordings: [cover, creep, live] })).search('creep', 1)
    expect(hits.map((h) => `${h.artist} — ${h.name}`)).toEqual(['Radiohead — Creep'])
  })

  it('throws UpstreamError when the request itself fails (network, timeout)', async () => {
    const fetchFn = vi.fn(async () => { throw Object.assign(new Error('The operation was aborted'), { name: 'TimeoutError' }) })
    await expect(provider(fetchFn as unknown as typeof fetch).search('creep', 10)).rejects.toBeInstanceOf(UpstreamError)
  })

  it('requires a userAgent', () => {
    expect(() => new MusicBrainzProvider({ userAgent: ' ' })).toThrow(/userAgent/)
  })
})

describe('buildLuceneQuery', () => {
  it('escapes Lucene special characters per term', () => {
    expect(buildLuceneQuery('AC/DC')).toBe('+((recording:AC\\/DC OR artist:AC\\/DC)) +status:official (recording:AC\\/DC)^2')
    expect(buildLuceneQuery('what? (live)')).toContain('+((recording:what\\? OR artist:what\\?) AND (recording:\\(live\\) OR artist:\\(live\\)))')
  })

  it('collapses whitespace and adds one boosting clause per artist/title split (both orders), then the all-title boost', () => {
    expect(buildLuceneQuery('  a   b   c ')).toBe(
      '+((recording:a OR artist:a) AND (recording:b OR artist:b) AND (recording:c OR artist:c)) +status:official '
      + '(artist:a AND recording:b AND recording:c)^3 (recording:a AND artist:b AND artist:c)^3 '
      + '(artist:a AND artist:b AND recording:c)^3 (recording:a AND recording:b AND artist:c)^3 '
      + '(recording:a AND recording:b AND recording:c)^2',
    )
  })

  it('has no split clauses for a single term and caps the number of terms', () => {
    expect(buildLuceneQuery('wonderwall')).toBe('+((recording:wonderwall OR artist:wonderwall)) +status:official (recording:wonderwall)^2')
    const many = buildLuceneQuery(Array.from({ length: 20 }, (_, i) => `t${i}`).join(' '))
    expect(many).not.toContain('t8')
    expect(many).toContain('t7')
  })
})

describe('rankRecordings', () => {
  const rec = (id: string, artist: string, title: string, score: number, releases = 1, first?: string) => ({
    id, score, title, 'first-release-date': first,
    'artist-credit': [{ name: artist }],
    releases: Array.from({ length: releases }, (_, i) => ({ title: `${title} release ${i}`, date: '2000' })),
  })

  it('merges same-song recordings regardless of case/punctuation and keeps the first id', () => {
    const groups = rankRecordings([rec('a', 'Radiohead', 'Creep', 100, 1, '1993'), rec('b', 'RADIOHEAD', 'creep!', 90, 3, '1992')])
    expect(groups).toHaveLength(1)
    expect(groups[0]).toMatchObject({ externalId: 'a', score: 100, firstReleaseYear: 1992 })
    expect(groups[0].releases).toHaveLength(4)
  })

  it('orders by score, then by release count (popularity proxy)', () => {
    const groups = rankRecordings([
      rec('cover', 'Cover Band', 'Song', 100, 2),
      rec('orig', 'Original', 'Song', 100, 40),
      rec('weak', 'Weak Match', 'Song', 60, 99),
    ])
    expect(groups.map((g) => g.externalId)).toEqual(['orig', 'cover', 'weak'])
  })

  it('ranks remix/live variants below plain titles regardless of score', () => {
    const groups = rankRecordings([
      rec('remix', 'Daft Punk', 'Get Lucky (Daft Punk remix)', 100, 50),
      rec('plain', 'Daft Punk', 'Get Lucky', 90, 5),
    ])
    expect(groups.map((g) => g.externalId)).toEqual(['plain', 'remix'])
  })
})

describe('isVariantTitle', () => {
  it.each(['Creep (acoustic)', 'Get Lucky (Daft Punk remix)', 'Wonderwall [live]', 'Song (2011 Remaster)', 'Song (feat. Someone)', 'Song (Take 3)'])(
    'flags %j', (title) => expect(isVariantTitle(title)).toBe(true),
  )
  it.each(['Creep', "(What's the Story) Morning Glory?", 'Live and Let Die', 'Song (Part 2)', 'Mix Tape'])(
    'leaves %j alone', (title) => expect(isVariantTitle(title)).toBe(false),
  )
})

describe('pickRelease', () => {
  it('prefers the earliest plain official album', () => {
    expect(pickRelease(creep.releases)?.title).toBe('Pablo Honey')
  })

  it('falls back to the earliest dated release of any kind', () => {
    const releases = [
      { title: 'Later single', date: '2001' },
      { title: 'Bootleg', date: '1999-05', status: 'Bootleg', 'release-group': { 'primary-type': 'Album' } },
    ]
    expect(pickRelease(releases)?.title).toBe('Bootleg')
  })

  it('falls back to the first release when none is dated', () => {
    expect(pickRelease([{ title: 'Undated' }, { title: 'Other' }])?.title).toBe('Undated')
    expect(pickRelease([])).toBeUndefined()
  })
})

describe('yearOf', () => {
  it('reads the year from partial and full MusicBrainz dates', () => {
    expect(yearOf('1993')).toBe(1993)
    expect(yearOf('1993-02')).toBe(1993)
    expect(yearOf('1993-02-22')).toBe(1993)
    expect(yearOf('')).toBeUndefined()
    expect(yearOf(undefined)).toBeUndefined()
    expect(yearOf('??')).toBeUndefined()
  })
})
