import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ConflictError } from '../errors'
import { formatId } from '../idGenerator'
import type { ContributorItem } from '../repositories/contributor'
import type { NominationItem } from '../repositories/nomination'
import type { PlaylistItem, PlaylistStatus } from '../repositories/playlist'
import type { SongItem } from '../repositories/song'
import { SpotifyNotFoundError, SpotifyUnauthorizedError, type SpotifyClient } from '../spotify/client'
import { mintSpotifyState } from '../spotify/state'

vi.mock('../repositories/playlist', () => ({
  findPlaylistById: vi.fn(),
  savePlaylist: vi.fn(),
}))
vi.mock('../repositories/contributor', () => ({
  findContributorById: vi.fn(),
  saveContributor: vi.fn(),
}))
vi.mock('../repositories/nomination', () => ({
  findNominationsByPlaylistId: vi.fn(),
}))
vi.mock('../repositories/song', () => ({
  findSongById: vi.fn(),
}))

import * as playlistRepo from '../repositories/playlist'
import * as contributorRepo from '../repositories/contributor'
import * as nominationRepo from '../repositories/nomination'
import * as songRepo from '../repositories/song'
import {
  exportPlaylistToSpotify,
  getAuthorizeUrl,
  getConnectionStatus,
  handleSpotifyCallback,
} from './spotify'

const TENANT_ID = 1
const PLAYLIST_DB_ID = 10n
const LEAD_DB_ID = 20n
const OTHER_CONTRIBUTOR_DB_ID = 21n
const SONG_DB_ID = 30n

const playlistId = formatId('play', PLAYLIST_DB_ID)

function playlist(status: PlaylistStatus, leadId: bigint | undefined, overrides: Partial<PlaylistItem> = {}): PlaylistItem {
  return {
    pk: `${TENANT_ID}#PLAYLIST`,
    sk: PLAYLIST_DB_ID.toString(),
    id: PLAYLIST_DB_ID,
    tenantId: TENANT_ID,
    name: 'Mix',
    status,
    leadContributorId: leadId,
    createdAt: 'now',
    updatedAt: 'now',
    ...overrides,
  }
}

function contributor(id: bigint, spotify?: ContributorItem['spotify']): ContributorItem {
  return {
    pk: `${TENANT_ID}#CONTRIBUTOR`,
    sk: id.toString(),
    id,
    tenantId: TENANT_ID,
    name: 'Thom',
    spotify,
    createdAt: 'now',
    updatedAt: 'now',
  }
}

function nomination(songId: bigint): NominationItem {
  return {
    pk: `${TENANT_ID}#NOMINATION`,
    sk: '1',
    id: 1n,
    tenantId: TENANT_ID,
    playlistId: PLAYLIST_DB_ID,
    songId,
    nominatedById: OTHER_CONTRIBUTOR_DB_ID,
    status: 'APPROVED',
    createdAt: 'now',
    updatedAt: 'now',
  }
}

function song(id: bigint, artist: string, name: string): SongItem {
  return { pk: `${TENANT_ID}#SONG`, sk: id.toString(), id, tenantId: TENANT_ID, artist, name, createdAt: 'now', updatedAt: 'now' }
}

function fakeSpotifyClient(overrides: Partial<SpotifyClient> = {}): SpotifyClient {
  return {
    exchangeCode: vi.fn(),
    refreshToken: vi.fn(),
    getCurrentUser: vi.fn(),
    searchTrackUri: vi.fn(),
    createPlaylist: vi.fn(),
    replacePlaylistTracks: vi.fn(),
    ...overrides,
  } as unknown as SpotifyClient
}

const activeConnection = { spotifyUserId: 'spotify-user-1', accessToken: 'access-token', refreshToken: 'refresh-token', expiresAt: new Date(Date.now() + 3600_000).toISOString() }

beforeEach(() => {
  vi.clearAllMocks()
  process.env.SPOTIFY_CLIENT_ID = 'test-client-id'
  process.env.SPOTIFY_REDIRECT_URI = 'https://ui.example/spotify/callback'
  process.env.SPOTIFY_STATE_SECRET = '00112233445566778899aabbccddeeff00112233445566778899aabbccddee'
})

describe('getAuthorizeUrl', () => {
  it('builds the Spotify authorize URL', () => {
    const { authorizeUrl } = getAuthorizeUrl(playlistId)
    const url = new URL(authorizeUrl)
    expect(url.origin + url.pathname).toBe('https://accounts.spotify.com/authorize')
    expect(url.searchParams.get('client_id')).toBe('test-client-id')
    expect(url.searchParams.get('redirect_uri')).toBe('https://ui.example/spotify/callback')
    expect(url.searchParams.get('response_type')).toBe('code')
    expect(url.searchParams.get('state')).toBeTruthy()
  })
})

describe('getConnectionStatus', () => {
  it('is true when the contributor has a Spotify connection', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(LEAD_DB_ID, activeConnection))
    const result = await getConnectionStatus(TENANT_ID, LEAD_DB_ID)
    expect(result.connected).toBe(true)
  })

  it('is false when the contributor has no Spotify connection', async () => {
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(LEAD_DB_ID))
    const result = await getConnectionStatus(TENANT_ID, LEAD_DB_ID)
    expect(result.connected).toBe(false)
  })
})

describe('handleSpotifyCallback', () => {
  it('saves a new connection and returns the state playlistId', async () => {
    const state = mintSpotifyState(playlistId)
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(LEAD_DB_ID))
    vi.mocked(contributorRepo.saveContributor).mockImplementation(async (item) => item)
    const spotifyClient = fakeSpotifyClient({
      exchangeCode: vi.fn().mockResolvedValue({ accessToken: 'access', refreshToken: 'refresh', expiresAt: '2030-01-01T00:00:00.000Z' }),
      getCurrentUser: vi.fn().mockResolvedValue({ id: 'spotify-user-1', displayName: 'Thom' }),
    })

    const result = await handleSpotifyCallback(TENANT_ID, LEAD_DB_ID, 'code-1', state, spotifyClient)

    expect(result.playlistId).toBe(playlistId)
    expect(contributorRepo.saveContributor).toHaveBeenCalledWith(
      expect.objectContaining({ spotify: { spotifyUserId: 'spotify-user-1', accessToken: 'access', refreshToken: 'refresh', expiresAt: '2030-01-01T00:00:00.000Z' } }),
    )
  })
})

describe('exportPlaylistToSpotify', () => {
  it('rejects a NEW playlist', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('NEW', LEAD_DB_ID))
    await expect(exportPlaylistToSpotify(TENANT_ID, playlistId, LEAD_DB_ID, fakeSpotifyClient())).rejects.toBeInstanceOf(ConflictError)
  })

  it('rejects an OPEN playlist', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('OPEN', LEAD_DB_ID))
    await expect(exportPlaylistToSpotify(TENANT_ID, playlistId, LEAD_DB_ID, fakeSpotifyClient())).rejects.toBeInstanceOf(ConflictError)
  })

  it('rejects a non-lead contributor', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('GUESSING', LEAD_DB_ID))
    await expect(
      exportPlaylistToSpotify(TENANT_ID, playlistId, OTHER_CONTRIBUTOR_DB_ID, fakeSpotifyClient()),
    ).rejects.toBeInstanceOf(ConflictError)
  })

  it('rejects when Spotify is not connected', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('GUESSING', LEAD_DB_ID))
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(LEAD_DB_ID))
    await expect(exportPlaylistToSpotify(TENANT_ID, playlistId, LEAD_DB_ID, fakeSpotifyClient())).rejects.toBeInstanceOf(ConflictError)
  })

  it('creates a fresh playlist with best-effort matching, reporting skipped songs', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('GUESSING', LEAD_DB_ID))
    vi.mocked(playlistRepo.savePlaylist).mockImplementation(async (item) => item)
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(LEAD_DB_ID, activeConnection))
    vi.mocked(nominationRepo.findNominationsByPlaylistId).mockResolvedValue([nomination(SONG_DB_ID), nomination(31n)])
    vi.mocked(songRepo.findSongById).mockImplementation(async (_tenantId, id) =>
      id === SONG_DB_ID ? song(SONG_DB_ID, 'Radiohead', 'Creep') : song(31n, 'Unknown', 'Nothing'),
    )
    const spotifyClient = fakeSpotifyClient({
      searchTrackUri: vi.fn().mockImplementation(async (_t, artist: string) =>
        artist === 'Radiohead' ? 'spotify:track:1' : undefined,
      ),
      createPlaylist: vi.fn().mockResolvedValue({ id: 'new-playlist-id', url: 'https://open.spotify.com/playlist/new-playlist-id' }),
      replacePlaylistTracks: vi.fn().mockResolvedValue(undefined),
    })

    const result = await exportPlaylistToSpotify(TENANT_ID, playlistId, LEAD_DB_ID, spotifyClient)

    expect(result.matchedCount).toBe(1)
    expect(result.skippedSongs).toEqual(['Unknown - Nothing'])
    expect(result.spotifyPlaylistId).toBe('new-playlist-id')
    expect(spotifyClient.replacePlaylistTracks).toHaveBeenCalledWith('access-token', 'new-playlist-id', ['spotify:track:1'])
    expect(playlistRepo.savePlaylist).toHaveBeenCalledWith(
      expect.objectContaining({ spotifyPlaylistId: 'new-playlist-id', spotifyPlaylistUrl: 'https://open.spotify.com/playlist/new-playlist-id' }),
    )
  })

  it('updates the existing Spotify playlist idempotently, without creating a new one', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(
      playlist('PUBLISHED', LEAD_DB_ID, { spotifyPlaylistId: 'existing-id', spotifyPlaylistUrl: 'https://open.spotify.com/playlist/existing-id' }),
    )
    vi.mocked(playlistRepo.savePlaylist).mockImplementation(async (item) => item)
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(LEAD_DB_ID, activeConnection))
    vi.mocked(nominationRepo.findNominationsByPlaylistId).mockResolvedValue([nomination(SONG_DB_ID)])
    vi.mocked(songRepo.findSongById).mockResolvedValue(song(SONG_DB_ID, 'Radiohead', 'Creep'))
    const spotifyClient = fakeSpotifyClient({
      searchTrackUri: vi.fn().mockResolvedValue('spotify:track:1'),
      replacePlaylistTracks: vi.fn().mockResolvedValue(undefined),
    })

    const result = await exportPlaylistToSpotify(TENANT_ID, playlistId, LEAD_DB_ID, spotifyClient)

    expect(result.spotifyPlaylistId).toBe('existing-id')
    expect(spotifyClient.replacePlaylistTracks).toHaveBeenCalledWith('access-token', 'existing-id', ['spotify:track:1'])
    expect(spotifyClient.createPlaylist).not.toHaveBeenCalled()
  })

  it('falls back to creating a fresh playlist when the stored one is gone (404)', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(
      playlist('PUBLISHED', LEAD_DB_ID, { spotifyPlaylistId: 'deleted-id', spotifyPlaylistUrl: 'https://open.spotify.com/playlist/deleted-id' }),
    )
    vi.mocked(playlistRepo.savePlaylist).mockImplementation(async (item) => item)
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(LEAD_DB_ID, activeConnection))
    vi.mocked(nominationRepo.findNominationsByPlaylistId).mockResolvedValue([])
    const spotifyClient = fakeSpotifyClient({
      replacePlaylistTracks: vi.fn().mockRejectedValueOnce(new SpotifyNotFoundError()),
      createPlaylist: vi.fn().mockResolvedValue({ id: 'fresh-id', url: 'https://open.spotify.com/playlist/fresh-id' }),
    })

    const result = await exportPlaylistToSpotify(TENANT_ID, playlistId, LEAD_DB_ID, spotifyClient)

    expect(result.spotifyPlaylistId).toBe('fresh-id')
    expect(result.matchedCount).toBe(0)
  })

  it('refreshes the token and retries once when Spotify rejects it with 401', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('GUESSING', LEAD_DB_ID))
    vi.mocked(playlistRepo.savePlaylist).mockImplementation(async (item) => item)
    const contributorItem = contributor(LEAD_DB_ID, activeConnection)
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributorItem)
    vi.mocked(contributorRepo.saveContributor).mockImplementation(async (item) => item)
    vi.mocked(nominationRepo.findNominationsByPlaylistId).mockResolvedValue([nomination(SONG_DB_ID)])
    vi.mocked(songRepo.findSongById).mockResolvedValue(song(SONG_DB_ID, 'Radiohead', 'Creep'))

    let searchCalls = 0
    const spotifyClient = fakeSpotifyClient({
      refreshToken: vi.fn().mockResolvedValue({ accessToken: 'fresh-access', refreshToken: 'fresh-refresh', expiresAt: new Date(Date.now() + 3600_000).toISOString() }),
      searchTrackUri: vi.fn().mockImplementation(async (token: string) => {
        searchCalls++
        if (token === 'access-token') throw new SpotifyUnauthorizedError()
        return 'spotify:track:1'
      }),
      createPlaylist: vi.fn().mockResolvedValue({ id: 'new-playlist-id', url: 'https://open.spotify.com/playlist/new-playlist-id' }),
      replacePlaylistTracks: vi.fn().mockResolvedValue(undefined),
    })

    const result = await exportPlaylistToSpotify(TENANT_ID, playlistId, LEAD_DB_ID, spotifyClient)

    expect(searchCalls).toBe(2)
    expect(result.matchedCount).toBe(1)
    expect(spotifyClient.refreshToken).toHaveBeenCalledWith('refresh-token')
  })
})
