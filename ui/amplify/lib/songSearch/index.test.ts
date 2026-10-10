import { afterEach, describe, expect, it } from 'vitest'
import { createSongSearchProvider } from './index'
import { MusicBrainzProvider } from './musicbrainz'
import { SpotifyProvider } from './spotify'

describe('createSongSearchProvider', () => {
  const env = { ...process.env }
  afterEach(() => { process.env = { ...env } })

  it('defaults to MusicBrainz', () => {
    delete process.env.SONG_SEARCH_PROVIDER
    expect(createSongSearchProvider()).toBeInstanceOf(MusicBrainzProvider)
  })

  it('reads SONG_SEARCH_PROVIDER', () => {
    process.env.SONG_SEARCH_PROVIDER = 'musicbrainz'
    expect(createSongSearchProvider().name).toBe('musicbrainz')
  })

  it('rejects an unknown provider name, listing the known ones', () => {
    expect(() => createSongSearchProvider('discogs')).toThrow(/Unknown song search provider "discogs" \(known: musicbrainz, spotify\)/)
  })

  it('creates the Spotify provider from SONG_SEARCH_PROVIDER + its client credentials', () => {
    process.env.SPOTIFY_CLIENT_ID = 'id'
    process.env.SPOTIFY_CLIENT_SECRET = 'secret'
    expect(createSongSearchProvider('spotify')).toBeInstanceOf(SpotifyProvider)
  })
})
