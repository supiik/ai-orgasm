/**
 * Abstraction over an external song-metadata catalogue used to pre-fill the song form
 * (artist / title / album / year). The rest of the API only ever talks to this interface —
 * `services/songSearch.ts` validates input and delegates, `index.ts` picks the concrete
 * provider from `SONG_SEARCH_PROVIDER` — so swapping MusicBrainz for another catalogue means
 * adding one file that implements `SongSearchProvider` and one line in the registry.
 */

export interface SongSearchHit {
  /** Which provider produced the hit (e.g. `musicbrainz`), so a client can attribute it. */
  source: string
  /** Provider-specific stable id of the recording (MusicBrainz MBID, …). */
  externalId: string
  artist: string
  name: string
  album?: string
  releaseYear?: number
  /** 0–100 relevance as reported by the provider, if it reports one. */
  score?: number
}

export interface SongSearchProvider {
  /** Registry key, also written into every hit's `source`. */
  readonly name: string
  /**
   * Free-text search (terms may match artist or title), best matches first.
   * `limit` is already validated and capped by the service.
   */
  search(query: string, limit: number): Promise<SongSearchHit[]>
}
