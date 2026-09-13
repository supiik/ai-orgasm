import { UpstreamError } from '../errors'
import { log } from '../logger'
import type { SongSearchHit, SongSearchProvider } from './provider'

/**
 * MusicBrainz (https://musicbrainz.org/doc/MusicBrainz_API) — open data, no API key.
 *
 * Two hard rules from their usage policy shape this class: every request must carry a
 * descriptive `User-Agent` naming the application and a contact (they throttle/503 generic
 * agents), and clients must stay under ~1 request/second. The first is why this runs in a
 * Lambda rather than the browser (JS can't set `User-Agent`); the second is why the UI
 * debounces and why `search-songs` gets a small reserved concurrency in `backend.ts`.
 *
 * The recording index is far more granular than the song form needs: every live take,
 * bootleg and remaster is its own "recording", every cover scores the same 100 as the
 * original, and titles like "Creep (Radiohead cover)" match a free-text "radiohead creep"
 * as well as the real thing does. So `search` asks for the largest page MusicBrainz allows,
 * boosts artist+title splits (see `buildLuceneQuery`), keeps only recordings with an
 * official release, then dedupes by artist+title and ranks the groups by how many releases
 * they appear on — the closest thing MusicBrainz has to popularity. Checked against the live
 * API: "artist title" queries land the original first; a title-only query ("wonderwall")
 * is a coin toss among hundreds of equally-scored covers, which no query shape can fix
 * without a popularity signal — hence the "artist and title" hint in the UI.
 */

export interface MusicBrainzOptions {
  /** `app/version (contact-url-or-email)` — mandatory per MusicBrainz's policy. */
  userAgent: string
  baseUrl?: string
  /** Per-request timeout; MusicBrainz searches typically take 0.3–2 s. */
  timeoutMs?: number
  /** Rows fetched per search before dedupe/ranking (MusicBrainz caps at 100). */
  fetchLimit?: number
  /** Injectable for tests. */
  fetchFn?: typeof fetch
}

/** The subset of a `/ws/2/recording?query=…&fmt=json` response this provider reads. */
interface RecordingSearchResponse {
  recordings?: Recording[]
}

interface Recording {
  id: string
  score?: number
  title: string
  'first-release-date'?: string
  'artist-credit'?: { name: string; joinphrase?: string }[]
  releases?: Release[]
}

interface Release {
  title: string
  date?: string
  status?: string
  'release-group'?: { 'primary-type'?: string; 'secondary-types'?: string[] }
}

/** Recordings of the same song (same artist credit + title), merged. */
interface Group {
  externalId: string
  artist: string
  name: string
  score: number
  releases: Release[]
  firstReleaseYear?: number
}

/**
 * MusicBrainz answers 503 "server busy" when a client exceeds its share; one retry after their
 * `Retry-After` (or a second, when absent) usually clears it. Capped so a long hint can't eat
 * the whole Lambda timeout.
 */
const DEFAULT_RETRY_AFTER_MS = 1100
const MAX_RETRY_AFTER_MS = 3000

export class MusicBrainzProvider implements SongSearchProvider {
  readonly name = 'musicbrainz'
  private readonly baseUrl: string
  private readonly userAgent: string
  private readonly timeoutMs: number
  private readonly fetchLimit: number
  private readonly fetchFn: typeof fetch

  constructor(options: MusicBrainzOptions) {
    if (!options.userAgent?.trim()) throw new Error('MusicBrainzProvider requires a userAgent')
    this.userAgent = options.userAgent
    this.baseUrl = (options.baseUrl ?? 'https://musicbrainz.org/ws/2').replace(/\/+$/, '')
    this.timeoutMs = options.timeoutMs ?? 8000
    this.fetchLimit = Math.min(options.fetchLimit ?? 100, 100)
    this.fetchFn = options.fetchFn ?? fetch
  }

  async search(query: string, limit: number): Promise<SongSearchHit[]> {
    const params = new URLSearchParams({ query: buildLuceneQuery(query), fmt: 'json', limit: String(this.fetchLimit) })
    const body = await this.get(`${this.baseUrl}/recording?${params}`)
    return rankRecordings(body.recordings ?? [])
      .slice(0, limit)
      .map((g) => this.toHit(g))
  }

  private async get(url: string, attempt = 1): Promise<RecordingSearchResponse> {
    let res: Response
    try {
      res = await this.fetchFn(url, {
        headers: { 'User-Agent': this.userAgent, Accept: 'application/json' },
        signal: AbortSignal.timeout(this.timeoutMs),
      })
    } catch (e) {
      log.warn('MusicBrainz request failed', { 'error.type': (e as Error).name, 'error.message': (e as Error).message })
      throw new UpstreamError('Song search is temporarily unavailable')
    }
    if (res.status === 503 && attempt === 1) {
      await new Promise((r) => setTimeout(r, retryAfterMs(res.headers.get('Retry-After'))))
      return this.get(url, attempt + 1)
    }
    if (!res.ok) {
      log.warn('MusicBrainz responded with an error', { 'http.response.status_code': res.status })
      throw new UpstreamError(res.status === 503 ? 'Song search is busy, try again shortly' : 'Song search is temporarily unavailable')
    }
    return (await res.json()) as RecordingSearchResponse
  }

  private toHit(group: Group): SongSearchHit {
    const release = pickRelease(group.releases)
    const years = [group.firstReleaseYear, yearOf(release?.date)].filter((y): y is number => y !== undefined)
    return {
      source: this.name,
      externalId: group.externalId,
      artist: group.artist,
      name: group.name,
      album: release?.title,
      releaseYear: years.length ? Math.min(...years) : undefined,
      score: group.score,
    }
  }
}

/** Longer queries than this are truncated before building the split clauses below. */
const MAX_TERMS = 8

/**
 * Every whitespace-separated term must match either the title or the artist (the required
 * clause), so a free-text "radiohead creep" finds the song without the caller having to know
 * which word is which. On top of that come optional boosting clauses, strongest first:
 *
 * - each way of splitting the terms into "artist words + title words" (`^3`) — a recording
 *   whose artist is "Radiohead" and title is "Creep" outscores a cover titled
 *   "Creep (Radiohead)", which only satisfies the required clause;
 * - all terms in the title (`^2`) — someone filling in a song form who types only
 *   "bohemian rhapsody" means the song, not the tribute band of that name.
 *
 * `status:official` drops bootleg-only recordings. Terms are escaped, not quoted, so Lucene
 * still stems/normalises them.
 */
export function buildLuceneQuery(query: string): string {
  const terms = query.split(/\s+/).map(escapeLucene).filter(Boolean).slice(0, MAX_TERMS)
  const field = (name: string, ts: string[]) => ts.map((t) => `${name}:${t}`).join(' AND ')
  const required = terms.map((t) => `(recording:${t} OR artist:${t})`).join(' AND ')
  const boosts: string[] = []
  for (let i = 1; i < terms.length; i++) {
    const head = terms.slice(0, i), tail = terms.slice(i)
    boosts.push(`(${field('artist', head)} AND ${field('recording', tail)})^3`)
    boosts.push(`(${field('recording', head)} AND ${field('artist', tail)})^3`)
  }
  boosts.push(`(${field('recording', terms)})^2`)
  return [`+(${required})`, '+status:official', ...boosts].join(' ')
}

function retryAfterMs(header: string | null): number {
  const seconds = Number(header)
  if (!header || !Number.isFinite(seconds) || seconds <= 0) return DEFAULT_RETRY_AFTER_MS
  return Math.min(seconds * 1000, MAX_RETRY_AFTER_MS)
}

function escapeLucene(term: string): string {
  return term.replace(/[+\-&|!(){}[\]^"~*?:\\/]/g, '\\$&')
}

/**
 * Merges recordings that are the same song (case/punctuation-insensitive artist + title),
 * then orders groups: plain titles before remix/live/… variants, then by MusicBrainz score,
 * then by the number of releases they appear on. Exported for tests.
 */
export function rankRecordings(recordings: Recording[]): Group[] {
  const groups = new Map<string, Group>()
  for (const r of recordings) {
    const artist = artistCredit(r)
    const key = `${normalise(artist)}|${normalise(r.title)}`
    const year = yearOf(r['first-release-date'])
    const group = groups.get(key)
    if (!group) {
      groups.set(key, { externalId: r.id, artist, name: r.title, score: r.score ?? 0, releases: [...(r.releases ?? [])], firstReleaseYear: year })
      continue
    }
    group.score = Math.max(group.score, r.score ?? 0)
    group.releases.push(...(r.releases ?? []))
    if (year !== undefined && (group.firstReleaseYear === undefined || year < group.firstReleaseYear)) group.firstReleaseYear = year
  }
  return [...groups.values()].sort(
    (a, b) =>
      Number(isVariantTitle(a.name)) - Number(isVariantTitle(b.name)) ||
      b.score - a.score ||
      b.releases.length - a.releases.length,
  )
}

/**
 * "Creep (acoustic)", "Get Lucky (Daft Punk remix)", "Wonderwall [live]" — a trailing bracketed
 * qualifier marks an alternate take of a song whose plain title is what the form wants. A
 * leading bracket ("(What's the Story) Morning Glory?") is part of the title and left alone.
 */
export function isVariantTitle(title: string): boolean {
  return /[([][^)\]]*\b(remix|mix|live|acoustic|edit|version|demo|instrumental|karaoke|cover|remaster(ed)?|radio|mono|stereo|feat\.?|ft\.?|extended|dub|unplugged|rehearsal|take \d+)\b[^)\]]*[)\]]\s*$/i.test(title)
}

function normalise(s: string): string {
  return s.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '')
}

/**
 * A recording usually appears on many releases (album, singles, compilations, remasters). The
 * original studio album is the one people expect in the form, so prefer: an official release
 * whose group is a plain "Album" (no Compilation/Live/Soundtrack secondary type) with the
 * earliest date; else any dated release, earliest first; else whatever comes first.
 */
export function pickRelease(releases: Release[]): Release | undefined {
  if (!releases.length) return undefined
  const dated = releases.filter((r) => yearOf(r.date) !== undefined)
  const byDate = (a: Release, b: Release) => a.date!.localeCompare(b.date!)
  const isPlainAlbum = (r: Release) =>
    r['release-group']?.['primary-type'] === 'Album' &&
    !(r['release-group']?.['secondary-types']?.length) &&
    (r.status === undefined || r.status === 'Official')
  return dated.filter(isPlainAlbum).sort(byDate)[0] ?? dated.sort(byDate)[0] ?? releases[0]
}

/** MusicBrainz dates are `YYYY`, `YYYY-MM` or `YYYY-MM-DD` — only the year matters here. */
export function yearOf(date: string | undefined): number | undefined {
  const m = date?.match(/^(\d{4})/)
  return m ? Number(m[1]) : undefined
}

/** Joins the credit as it appears on the record: "Simon" + " & " + "Garfunkel". */
function artistCredit(recording: Recording): string {
  return (recording['artist-credit'] ?? []).map((c) => `${c.name}${c.joinphrase ?? ''}`).join('').trim()
}
