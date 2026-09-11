import { ConflictError, NotFoundError } from '../errors'
import type { PageRequest } from '../http'
import { formatId, generateId, parseId } from '../idGenerator'
import { findContributorById } from '../repositories/contributor'
import {
  declinePendingNominations,
  findNominationById,
  findNominationsByPlaylistId,
  findNominationsBySongId,
  saveNomination,
  existsNominationForPlaylistAndSong,
  type NominationItem,
  type NominationStatus,
} from '../repositories/nomination'
import { findAllPlaylistsByTenant, findPlaylistById, savePlaylist, type PlaylistItem } from '../repositories/playlist'
import { findSongById } from '../repositories/song'
import { deleteGuessesByPlaylistAndGuesser, findGuessesByPlaylistId, saveGuess, type GuessItem } from '../repositories/guess'
import { existsGuessSubmission, saveGuessSubmission, type GuessSubmissionItem } from '../repositories/guessSubmission'
import {
  deleteSongRatingsByPlaylistAndContributor,
  findSongRatingsByPlaylistId,
  saveSongRating,
  type SongRatingItem,
} from '../repositories/songRating'
import { findAllRankingsByTenant, saveRanking, type PlaylistRankingItem } from '../repositories/playlistRanking'
import { memoize } from '../memo'
import {
  requirePlaylistItem as requirePlaylistItemById,
  toPlaylistResponse as basePlaylistResponse,
  toPlaylistResponses,
  type PlaylistResponse,
} from './playlist'

// ---------------------------------------------------------------------------
// DTOs — ported verbatim from backend-dynamo's com.orgasm.dynamo.orgasm.* records
// ---------------------------------------------------------------------------

/**
 * The acting contributor is NEVER taken from the request body — every workflow method receives it
 * as `actorId`, resolved by `withAuth` from the caller's verified Cognito token. Trusting a
 * body-supplied id made the "only the lead contributor can ..." checks below self-authorizing:
 * a caller could read `leadContributorId` off the playlist and send it back to pass them.
 */
export interface OpenPlaylistRequest {
  deadline: string
}

export interface NominateSongRequest {
  songId: string
}

export interface NominationResponse {
  id: string
  playlistId: string
  songId: string
  nominatedById: string
  status: NominationStatus
  version?: number
  createdAt: string
  updatedAt: string
}

export interface SongNominationResponse {
  id: string
  playlistId: string
  playlistName?: string
  nominatedById: string
  nominatedByName?: string
  status: NominationStatus
}

export interface GuessSelection {
  nominationId: string
  guessedContributorId: string
}

export interface SubmitGuessesRequest {
  guesses?: GuessSelection[]
}

export interface GuessResponse {
  nominationId: string
  guesserId: string
  guessedContributorId: string
}

export interface RankingResponse {
  playlistId: string
  playlistName?: string
  contributorId: string
  contributorName?: string
  contributorAvatarUrl?: string
  rankPosition: number
  correctGuesses: number
  totalGuesses: number
}

export interface RatingEntry {
  nominationId: string
  points: number
}

export interface SubmitRatingsRequest {
  ratings: RatingEntry[]
}

export interface SongRatingResponse {
  nominationId: string
  contributorId: string
  contributorName?: string
  points: number
}

// ---------------------------------------------------------------------------
// Internal helpers — mirror OrgasmService.java's private require*/toXResponse methods
// ---------------------------------------------------------------------------

async function requirePlaylist(tenantId: number, playlistId: string): Promise<PlaylistItem> {
  return requirePlaylistItemById(tenantId, playlistId)
}

async function requirePlaylistByDbId(tenantId: number, playlistDbId: bigint): Promise<PlaylistItem> {
  return requirePlaylistItemById(tenantId, formatId('play', playlistDbId))
}

async function requireSong(tenantId: number, songId: bigint): Promise<void> {
  const item = await findSongById(tenantId, songId)
  if (!item || item.deletedAt) throw new NotFoundError(`Song not found: ${songId}`)
}

async function requireNomination(tenantId: number, nominationId: string): Promise<NominationItem> {
  const item = await findNominationById(tenantId, parseId(nominationId))
  if (!item || item.deletedAt) throw new NotFoundError(`Nomination not found: ${nominationId}`)
  return item
}

function isLeadContributor(playlist: PlaylistItem, contributorDbId: bigint): boolean {
  return playlist.leadContributorId !== undefined && playlist.leadContributorId === contributorDbId
}

function toNominationResponse(item: NominationItem): NominationResponse {
  return {
    id: formatId('nom', item.id),
    playlistId: formatId('play', item.playlistId),
    songId: formatId('song', item.songId),
    nominatedById: formatId('cont', item.nominatedById),
    status: item.status,
    version: item.version,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  }
}

// ---------------------------------------------------------------------------
// Public workflow methods — one per OrgasmController endpoint
// ---------------------------------------------------------------------------

export async function openPlaylist(
  tenantId: number,
  playlistId: string,
  actorId: bigint,
  request: OpenPlaylistRequest,
): Promise<PlaylistResponse> {
  const playlist = await requirePlaylist(tenantId, playlistId)
  if (playlist.status !== 'NEW') throw new ConflictError('Playlist must be NEW to open')

  playlist.deadline = request.deadline
  playlist.leadContributorId = actorId
  playlist.status = 'OPEN'
  playlist.updatedAt = new Date().toISOString()

  return basePlaylistResponse(tenantId, await savePlaylist(playlist))
}

export async function findPlaylistsByContributor(
  tenantId: number,
  contributorId: string,
  page: PageRequest | undefined,
): Promise<{ content: PlaylistResponse[]; totalElements: number }> {
  const parsedContributorId = parseId(contributorId)
  const all = (await findAllPlaylistsByTenant(tenantId))
    .filter((item) => !item.deletedAt)
    .filter((item) => item.leadContributorId === parsedContributorId)

  const paged = page ? all.slice(page.page * page.size, page.page * page.size + page.size) : all
  return { content: await toPlaylistResponses(tenantId, paged), totalElements: all.length }
}

export async function nominateSong(
  tenantId: number,
  playlistId: string,
  actorId: bigint,
  request: NominateSongRequest,
): Promise<NominationResponse> {
  const playlist = await requirePlaylist(tenantId, playlistId)
  if (playlist.status !== 'OPEN') throw new ConflictError('Playlist must be OPEN to nominate a song')
  if (playlist.deadline && new Date() > new Date(playlist.deadline)) {
    throw new ConflictError('Nomination deadline has passed')
  }

  const songId = parseId(request.songId)
  await requireSong(tenantId, songId)

  const playlistDbId = parseId(playlistId)
  if (await existsNominationForPlaylistAndSong(playlistDbId, songId)) {
    throw new ConflictError('Song has already been nominated for this playlist')
  }

  const now = new Date().toISOString()
  const id = generateId()
  const item: NominationItem = {
    pk: `${tenantId}#NOMINATION`,
    sk: id.toString(),
    id,
    tenantId,
    playlistId: playlistDbId,
    songId,
    nominatedById: actorId,
    status: 'PENDING',
    createdAt: now,
    updatedAt: now,
  }
  return toNominationResponse(await saveNomination(item))
}

export async function findNominations(
  tenantId: number,
  playlistId: string,
  page: PageRequest | undefined,
): Promise<{ content: NominationResponse[]; totalElements: number }> {
  const playlistDbId = parseId(playlistId)
  const all = (await findNominationsByPlaylistId(playlistDbId)).filter((item) => !item.deletedAt)
  const paged = page ? all.slice(page.page * page.size, page.page * page.size + page.size) : all
  return { content: paged.map(toNominationResponse), totalElements: all.length }
}

export async function findNominationsBySong(tenantId: number, songId: string): Promise<SongNominationResponse[]> {
  const songDbId = parseId(songId)
  const items = (await findNominationsBySongId(songDbId)).filter((item) => !item.deletedAt)
  const lookupPlaylist = memoize((id: bigint) => findPlaylistById(tenantId, id))
  const lookupContributor = memoize((id: bigint) => findContributorById(tenantId, id))
  return Promise.all(
    items.map(async (item) => {
      const playlist = await lookupPlaylist(item.playlistId)
      const nominatedBy = await lookupContributor(item.nominatedById)
      return {
        id: formatId('nom', item.id),
        playlistId: formatId('play', item.playlistId),
        playlistName: playlist?.name,
        nominatedById: formatId('cont', item.nominatedById),
        nominatedByName: nominatedBy?.name,
        status: item.status,
      }
    }),
  )
}

async function reviewNomination(
  tenantId: number,
  nominationId: string,
  actorId: bigint,
  newStatus: NominationStatus,
): Promise<NominationResponse> {
  const nomination = await requireNomination(tenantId, nominationId)
  if (nomination.status !== 'PENDING') throw new ConflictError('Nomination is not pending')

  const playlist = await requirePlaylistByDbId(tenantId, nomination.playlistId)
  if (!isLeadContributor(playlist, actorId)) {
    throw new ConflictError('Only the lead contributor can review nominations')
  }

  nomination.status = newStatus
  nomination.updatedAt = new Date().toISOString()
  return toNominationResponse(await saveNomination(nomination))
}

export function approveNomination(tenantId: number, nominationId: string, actorId: bigint) {
  return reviewNomination(tenantId, nominationId, actorId, 'APPROVED')
}

export function declineNomination(tenantId: number, nominationId: string, actorId: bigint) {
  return reviewNomination(tenantId, nominationId, actorId, 'DECLINED')
}

export async function startGuessing(tenantId: number, playlistId: string, actorId: bigint): Promise<PlaylistResponse> {
  const playlist = await requirePlaylist(tenantId, playlistId)
  if (playlist.status !== 'OPEN') throw new ConflictError('Playlist must be OPEN to start guessing')

  if (!isLeadContributor(playlist, actorId)) {
    throw new ConflictError('Only the lead contributor can start guessing')
  }

  const playlistDbId = parseId(playlistId)
  const deadlinePassed = !playlist.deadline || new Date() > new Date(playlist.deadline)
  const nominations = (await findNominationsByPlaylistId(playlistDbId)).filter((item) => !item.deletedAt)
  const noPending = !nominations.some((item) => item.status === 'PENDING')
  if (!deadlinePassed && !noPending) {
    throw new ConflictError('Deadline has not passed and there are still pending nominations')
  }

  const now = new Date()
  const nowIso = now.toISOString()
  await declinePendingNominations(playlistDbId, nowIso)

  const guessingDeadline = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000)
  playlist.guessingDeadline = guessingDeadline.toISOString()
  playlist.status = 'GUESSING'
  playlist.updatedAt = nowIso

  return basePlaylistResponse(tenantId, await savePlaylist(playlist))
}

export async function submitGuesses(
  tenantId: number,
  playlistId: string,
  actorId: bigint,
  request: SubmitGuessesRequest,
): Promise<void> {
  const playlist = await requirePlaylist(tenantId, playlistId)
  if (playlist.status !== 'GUESSING') throw new ConflictError('Playlist must be GUESSING to submit guesses')

  const playlistDbId = parseId(playlistId)
  await deleteGuessesByPlaylistAndGuesser(playlistDbId, actorId)

  const now = new Date().toISOString()
  if (request.guesses) {
    for (const selection of request.guesses) {
      const nominationDbId = parseId(selection.nominationId)
      const nomination = await findNominationById(tenantId, nominationDbId)
      if (!nomination) throw new NotFoundError(`Nomination not found: ${selection.nominationId}`)
      const guessedContributorId = parseId(selection.guessedContributorId)

      const id = generateId()
      const guess: GuessItem = {
        pk: `${tenantId}#GUESS`,
        sk: id.toString(),
        id,
        tenantId,
        playlistId: playlistDbId,
        nominationId: nominationDbId,
        guesserId: actorId,
        guessedContributorId,
        createdAt: now,
      }
      await saveGuess(guess)
    }
  }

  if (!(await existsGuessSubmission(playlistDbId, actorId))) {
    const id = generateId()
    const submission: GuessSubmissionItem = {
      pk: `${tenantId}#GUESS_SUBMISSION`,
      sk: id.toString(),
      id,
      tenantId,
      playlistId: playlistDbId,
      contributorId: actorId,
      createdAt: now,
      updatedAt: now,
    }
    await saveGuessSubmission(submission)
  }
}

export async function getGuesses(playlistId: string): Promise<GuessResponse[]> {
  const playlistDbId = parseId(playlistId)
  const items = await findGuessesByPlaylistId(playlistDbId)
  return items.map((item) => ({
    nominationId: formatId('nom', item.nominationId),
    guesserId: formatId('cont', item.guesserId),
    guessedContributorId: formatId('cont', item.guessedContributorId),
  }))
}

export async function publishPlaylist(tenantId: number, playlistId: string, actorId: bigint): Promise<PlaylistResponse> {
  const playlist = await requirePlaylist(tenantId, playlistId)
  if (playlist.status !== 'GUESSING') throw new ConflictError('Playlist must be GUESSING to publish')

  if (!isLeadContributor(playlist, actorId)) {
    throw new ConflictError('Only the lead contributor can publish')
  }

  playlist.status = 'PUBLISHED'
  playlist.updatedAt = new Date().toISOString()
  const saved = await savePlaylist(playlist)

  await saveRankingsForPlaylist(tenantId, parseId(playlistId))

  return basePlaylistResponse(tenantId, saved)
}

/** Standard competition ranking: ties share a rank, the next rank skips accordingly. */
async function saveRankingsForPlaylist(tenantId: number, playlistDbId: bigint): Promise<void> {
  const nominations = await findNominationsByPlaylistId(playlistDbId)
  const approvedNominators = new Map<string, bigint>()
  for (const n of nominations) {
    if (n.status === 'APPROVED') approvedNominators.set(n.id.toString(), n.nominatedById)
  }

  const guesses = await findGuessesByPlaylistId(playlistDbId)
  const byGuesser = new Map<string, GuessItem[]>()
  for (const g of guesses) {
    const key = g.guesserId.toString()
    const list = byGuesser.get(key) ?? []
    list.push(g)
    byGuesser.set(key, list)
  }

  const tallies = [...byGuesser.entries()]
    .map(([, items]) => {
      const guesserId = items[0].guesserId
      const total = items.length
      const correct = items.filter((item) => {
        const nominator = approvedNominators.get(item.nominationId.toString())
        return nominator !== undefined && nominator === item.guessedContributorId
      }).length
      return { guesserId, correct, total }
    })
    .sort((a, b) => b.correct - a.correct)

  const now = new Date().toISOString()
  let rank = 0
  let previousCorrect = -1
  let position = 0
  for (const tally of tallies) {
    position++
    if (tally.correct !== previousCorrect) {
      rank = position
      previousCorrect = tally.correct
    }

    const id = generateId()
    const item: PlaylistRankingItem = {
      pk: `${tenantId}#PLAYLIST_RANKING`,
      sk: id.toString(),
      id,
      tenantId,
      playlistId: playlistDbId,
      contributorId: tally.guesserId,
      rankPosition: rank,
      correctGuesses: tally.correct,
      totalGuesses: tally.total,
      createdAt: now,
      updatedAt: now,
    }
    await saveRanking(item)
  }
}

export async function getRankings(tenantId: number): Promise<RankingResponse[]> {
  const rankings = (await findAllRankingsByTenant(tenantId))
    .filter((item) => !item.deletedAt)
    .sort((a, b) => (a.playlistId < b.playlistId ? -1 : a.playlistId > b.playlistId ? 1 : a.rankPosition - b.rankPosition))

  // Every ranking row for a playlist shares that playlist, and contributors recur across
  // playlists — this is the tenant-wide unbounded list, so the memo matters most here.
  const lookupPlaylist = memoize((id: bigint) => findPlaylistById(tenantId, id))
  const lookupContributor = memoize((id: bigint) => findContributorById(tenantId, id))
  return Promise.all(
    rankings.map(async (item) => {
      const playlist = await lookupPlaylist(item.playlistId)
      const contributor = await lookupContributor(item.contributorId)
      return {
        playlistId: formatId('play', item.playlistId),
        playlistName: playlist?.name,
        contributorId: formatId('cont', item.contributorId),
        contributorName: contributor?.name,
        contributorAvatarUrl: contributor?.avatarUrl,
        rankPosition: item.rankPosition,
        correctGuesses: item.correctGuesses,
        totalGuesses: item.totalGuesses,
      }
    }),
  )
}

const RATING_POINTS: Record<string, number[]> = {
  LINEAR: [1, 2, 3],
  FIBONACCI: [5, 8, 13],
  BEST_SONG: [1],
}

function validateRatings(ratingType: string, ratings: RatingEntry[]): void {
  const expected = RATING_POINTS[ratingType]
  if (!expected) throw new ConflictError(`Unknown rating type: ${ratingType}`)
  const submitted = ratings.map((r) => r.points).sort((a, b) => a - b)
  if (submitted.length !== expected.length || !submitted.every((v, i) => v === expected[i])) {
    throw new ConflictError(`Ratings must be exactly ${JSON.stringify(expected)}`)
  }
}

export async function submitRatings(
  tenantId: number,
  playlistId: string,
  actorId: bigint,
  request: SubmitRatingsRequest,
): Promise<void> {
  const playlist = await requirePlaylist(tenantId, playlistId)
  if (playlist.status !== 'PUBLISHED') throw new ConflictError('Playlist must be PUBLISHED to submit ratings')
  if (!playlist.ratingType) throw new ConflictError('Playlist has no rating type configured')

  validateRatings(playlist.ratingType, request.ratings)

  const playlistDbId = parseId(playlistId)
  await deleteSongRatingsByPlaylistAndContributor(playlistDbId, actorId)

  const now = new Date().toISOString()
  for (const entry of request.ratings) {
    const nominationDbId = parseId(entry.nominationId)
    const nomination = await findNominationById(tenantId, nominationDbId)
    if (!nomination) throw new NotFoundError(`Nomination not found: ${entry.nominationId}`)
    if (nomination.nominatedById === actorId) {
      throw new ConflictError('Cannot rate your own nomination')
    }

    const id = generateId()
    const item: SongRatingItem = {
      pk: `${tenantId}#SONG_RATING`,
      sk: id.toString(),
      id,
      tenantId,
      playlistId: playlistDbId,
      contributorId: actorId,
      nominationId: nominationDbId,
      points: entry.points,
      createdAt: now,
      updatedAt: now,
    }
    await saveSongRating(item)
  }
}

export async function getSongRatings(tenantId: number, playlistId: string): Promise<SongRatingResponse[]> {
  const playlistDbId = parseId(playlistId)
  const items = (await findSongRatingsByPlaylistId(playlistDbId)).filter((item) => !item.deletedAt)
  const lookupContributor = memoize((id: bigint) => findContributorById(tenantId, id))
  return Promise.all(
    items.map(async (item) => {
      const contributor = await lookupContributor(item.contributorId)
      return {
        nominationId: formatId('nom', item.nominationId),
        contributorId: formatId('cont', item.contributorId),
        contributorName: contributor?.name,
        points: item.points,
      }
    }),
  )
}
