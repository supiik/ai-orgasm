import { ConflictError, NotFoundError } from '../errors'
import { log } from '../logger'
import { memoize } from '../memo'
import {
  SpotifyClient,
  SpotifyNotFoundError,
  SpotifyUnauthorizedError,
  type SpotifyPlaylist,
} from '../spotify/client'
import { mintSpotifyState, verifySpotifyState } from '../spotify/state'
import { findContributorById, saveContributor, type ContributorItem } from '../repositories/contributor'
import { findNominationsByPlaylistId } from '../repositories/nomination'
import { findSongById } from '../repositories/song'
import { savePlaylist, type PlaylistItem } from '../repositories/playlist'
import { requirePlaylistItem as requirePlaylistItemById } from './playlist'

/**
 * Spotify OAuth (per-contributor) + "export a playlist's approved nominations to a Spotify
 * playlist". A straight TS port of the Spring side's `SpotifyService.java` (see CLAUDE.md's
 * "Spotify playlist export" section) — same status/lead checks, same best-effort track
 * matching, same idempotent update-in-place export. As with the rest of this module, the acting
 * contributor is always `actorId`/`contributorId` resolved by `withAuth` from the verified
 * Cognito token — never a body-supplied id.
 */

const SCOPES = 'playlist-modify-public playlist-modify-private'
const REFRESH_SKEW_MS = 60_000

let defaultClient: SpotifyClient | undefined
function client(): SpotifyClient {
  return (defaultClient ??= new SpotifyClient({
    clientId: process.env.SPOTIFY_CLIENT_ID ?? '',
    clientSecret: process.env.SPOTIFY_CLIENT_SECRET ?? '',
  }))
}

export interface SpotifyAuthorizeResponse {
  authorizeUrl: string
}

export interface SpotifyConnectionStatusResponse {
  connected: boolean
}

export interface SpotifyCallbackResponse {
  playlistId?: string
}

export interface SpotifyExportResponse {
  spotifyPlaylistId: string
  spotifyPlaylistUrl: string
  matchedCount: number
  skippedSongs: string[]
}

async function requireContributor(tenantId: number, contributorId: bigint): Promise<ContributorItem> {
  const item = await findContributorById(tenantId, contributorId)
  if (!item || item.deletedAt) throw new NotFoundError(`Contributor not found: ${contributorId}`)
  return item
}

/** Refreshes the contributor's Spotify token if it's within `REFRESH_SKEW_MS` of expiry, and
 * retries `action` once (after a forced refresh) if Spotify still rejects the token with 401 —
 * the same refresh-before-use-plus-retry-once shape as the Spring port's `SpotifyTokenService`. */
async function withValidAccessToken<T>(
  contributor: ContributorItem,
  spotifyClient: SpotifyClient,
  action: (accessToken: string) => Promise<T>,
): Promise<T> {
  const token = await ensureFreshToken(contributor, spotifyClient)
  try {
    return await action(token)
  } catch (e) {
    if (!(e instanceof SpotifyUnauthorizedError)) throw e
    return action(await forceRefresh(contributor, spotifyClient))
  }
}

async function ensureFreshToken(contributor: ContributorItem, spotifyClient: SpotifyClient): Promise<string> {
  const connection = contributor.spotify
  if (!connection) throw new ConflictError('Spotify is not connected')
  if (Date.now() >= new Date(connection.expiresAt).getTime() - REFRESH_SKEW_MS) {
    return forceRefresh(contributor, spotifyClient)
  }
  return connection.accessToken
}

async function forceRefresh(contributor: ContributorItem, spotifyClient: SpotifyClient): Promise<string> {
  const connection = contributor.spotify
  if (!connection) throw new ConflictError('Spotify is not connected')
  const tokens = await spotifyClient.refreshToken(connection.refreshToken)
  contributor.spotify = {
    spotifyUserId: connection.spotifyUserId,
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken || connection.refreshToken,
    expiresAt: tokens.expiresAt,
  }
  contributor.updatedAt = new Date().toISOString()
  await saveContributor(contributor)
  return contributor.spotify.accessToken
}

export function getAuthorizeUrl(playlistId: string | undefined): SpotifyAuthorizeResponse {
  const state = mintSpotifyState(playlistId)
  const params = new URLSearchParams({
    client_id: process.env.SPOTIFY_CLIENT_ID ?? '',
    response_type: 'code',
    redirect_uri: process.env.SPOTIFY_REDIRECT_URI ?? '',
    scope: SCOPES,
    state,
  })
  return { authorizeUrl: `https://accounts.spotify.com/authorize?${params}` }
}

export async function getConnectionStatus(
  tenantId: number,
  contributorId: bigint,
): Promise<SpotifyConnectionStatusResponse> {
  const contributor = await requireContributor(tenantId, contributorId)
  return { connected: contributor.spotify !== undefined }
}

export async function handleSpotifyCallback(
  tenantId: number,
  contributorId: bigint,
  code: string,
  state: string,
  spotifyClient: SpotifyClient = client(),
): Promise<SpotifyCallbackResponse> {
  const playlistId = verifySpotifyState(state)
  const contributor = await requireContributor(tenantId, contributorId)
  const tokens = await spotifyClient.exchangeCode(code, process.env.SPOTIFY_REDIRECT_URI ?? '')
  const user = await spotifyClient.getCurrentUser(tokens.accessToken)

  contributor.spotify = {
    spotifyUserId: user.id,
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
    expiresAt: tokens.expiresAt,
  }
  contributor.updatedAt = new Date().toISOString()
  await saveContributor(contributor)

  return { playlistId }
}

export async function exportPlaylistToSpotify(
  tenantId: number,
  playlistId: string,
  actorId: bigint,
  spotifyClient: SpotifyClient = client(),
): Promise<SpotifyExportResponse> {
  const playlist = await requirePlaylistItemById(tenantId, playlistId)
  if (playlist.status === 'NEW' || playlist.status === 'OPEN') {
    throw new ConflictError(`Nominations are not final yet, playlist status is ${playlist.status}`)
  }
  if (playlist.leadContributorId === undefined || playlist.leadContributorId !== actorId) {
    throw new ConflictError('Only the lead contributor can export a playlist to Spotify')
  }
  const contributor = await requireContributor(tenantId, actorId)
  if (!contributor.spotify) throw new ConflictError('Spotify is not connected')

  const approved = (await findNominationsByPlaylistId(playlist.id)).filter((n) => n.status === 'APPROVED')
  const lookupSong = memoize((id: bigint) => findSongById(tenantId, id))

  const trackUris: string[] = []
  const skippedSongs: string[] = []
  for (const nomination of approved) {
    const song = await lookupSong(nomination.songId)
    if (!song) continue
    const uri = await withValidAccessToken(contributor, spotifyClient, (token) =>
      spotifyClient.searchTrackUri(token, song.artist, song.name),
    )
    if (uri) trackUris.push(uri)
    else skippedSongs.push(`${song.artist} - ${song.name}`)
  }

  const spotifyPlaylist = await createOrUpdatePlaylist(contributor, playlist, trackUris, spotifyClient)

  playlist.spotifyPlaylistId = spotifyPlaylist.id
  playlist.spotifyPlaylistUrl = spotifyPlaylist.url
  playlist.updatedAt = new Date().toISOString()
  await savePlaylist(playlist)

  return {
    spotifyPlaylistId: spotifyPlaylist.id,
    spotifyPlaylistUrl: spotifyPlaylist.url,
    matchedCount: trackUris.length,
    skippedSongs,
  }
}

/** Idempotent: replaces the existing Spotify playlist's tracks when one is already stored,
 * falling back to creating a fresh one on a stale/deleted id (404) — never accumulates
 * duplicate playlists in the contributor's Spotify account across repeated exports. */
async function createOrUpdatePlaylist(
  contributor: ContributorItem,
  playlist: PlaylistItem,
  trackUris: string[],
  spotifyClient: SpotifyClient,
): Promise<SpotifyPlaylist> {
  const existingId = playlist.spotifyPlaylistId
  if (existingId) {
    try {
      await withValidAccessToken(contributor, spotifyClient, (token) =>
        spotifyClient.replacePlaylistTracks(token, existingId, trackUris),
      )
      return { id: existingId, url: playlist.spotifyPlaylistUrl ?? '' }
    } catch (e) {
      if (!(e instanceof SpotifyNotFoundError)) throw e
      log.info('Stored Spotify playlist no longer exists, creating a new one', { spotifyPlaylistId: existingId })
    }
  }
  const spotifyUserId = contributor.spotify!.spotifyUserId
  const created = await withValidAccessToken(contributor, spotifyClient, (token) =>
    spotifyClient.createPlaylist(token, spotifyUserId, playlist.name, `Created by Orgasm from playlist "${playlist.name}"`),
  )
  if (trackUris.length > 0) {
    await withValidAccessToken(contributor, spotifyClient, (token) =>
      spotifyClient.replacePlaylistTracks(token, created.id, trackUris),
    )
  }
  return created
}
