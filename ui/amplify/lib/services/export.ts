import { formatId } from '../idGenerator'
import { findAllContributorsByTenant, type ContributorItem } from '../repositories/contributor'
import { findAllGuessesByTenant, type GuessItem } from '../repositories/guess'
import { findAllGuessSubmissionsByTenant, type GuessSubmissionItem } from '../repositories/guessSubmission'
import { findAllNominationsByTenant, type NominationItem } from '../repositories/nomination'
import { findAllPlaylistsByTenant, type PlaylistItem } from '../repositories/playlist'
import { findAllRankingsByTenant, type PlaylistRankingItem } from '../repositories/playlistRanking'
import { findAllSongsByTenant, type SongItem } from '../repositories/song'
import { findAllSongRatingsByTenant, type SongRatingItem } from '../repositories/songRating'
import { requireOrganization } from './admin'

/**
 * Full data export of one organization (tenant) — everything the app holds about it, in one
 * JSON document, for a customer who is leaving and wants their history. Admin-only (the
 * `admin` AuthMode): it dumps every member's email and every playlist ever run, which is more
 * than any single contributor can see through the regular endpoints.
 *
 * Shape rules, so the file is useful without the app:
 * - Ids and foreign keys use the same prefixed external form the REST responses use
 *   (`cont-…`, `play-…`, `song-…`, `nom-…`), so rows cross-reference each other exactly like
 *   the API does. Storage keys (`pk`/`sk`) and the optimistic-lock `version` are dropped.
 *   Guess/GuessSubmission/SongRating/PlaylistRanking rows have no id in any REST response, so
 *   the export mints `guess-`/`gsub-`/`rate-`/`rank-` prefixes for them with the same encoding.
 * - Soft-deleted rows are INCLUDED, with their `deletedAt` — this is "whole history", not the
 *   live view the list endpoints give. `Guess` is hard-deleted so it never carries the field.
 * - `cognitoSub` is dropped: it is our identity provider's handle, not the customer's data.
 * - The document says what it is (`format`/`formatVersion`) so a future importer can check.
 */
export const EXPORT_FORMAT = 'orgasm-organization-export'
export const EXPORT_FORMAT_VERSION = 1

export interface OrganizationExport {
  format: typeof EXPORT_FORMAT
  formatVersion: typeof EXPORT_FORMAT_VERSION
  exportedAt: string
  organization: { id: number; slug: string; name: string; allowedDomain?: string }
  contributors: ExportedContributor[]
  songs: ExportedSong[]
  playlists: ExportedPlaylist[]
  nominations: ExportedNomination[]
  guessSubmissions: ExportedGuessSubmission[]
  guesses: ExportedGuess[]
  songRatings: ExportedSongRating[]
  playlistRankings: ExportedPlaylistRanking[]
  counts: Record<'contributors' | 'songs' | 'playlists' | 'nominations' | 'guessSubmissions' | 'guesses' | 'songRatings' | 'playlistRankings', number>
}

interface Audit {
  createdAt: string
  updatedAt: string
  deletedAt?: string
}

export interface ExportedContributor extends Audit {
  id: string
  name: string
  email?: string
  avatarUrl?: string
}

export interface ExportedSong extends Audit {
  id: string
  artist: string
  name: string
  album?: string
  releaseYear?: number
  url?: string
}

export interface ExportedPlaylist extends Audit {
  id: string
  name: string
  description?: string
  status: PlaylistItem['status']
  ratingType?: string
  leadContributorId?: string
  deadline?: string
  guessingDeadline?: string
}

export interface ExportedNomination extends Audit {
  id: string
  playlistId: string
  songId: string
  nominatedById: string
  status: NominationItem['status']
}

export interface ExportedGuessSubmission extends Audit {
  id: string
  playlistId: string
  contributorId: string
}

export interface ExportedGuess {
  id: string
  playlistId: string
  nominationId: string
  guesserId: string
  guessedContributorId: string
  createdAt: string
}

export interface ExportedSongRating extends Audit {
  id: string
  playlistId: string
  contributorId: string
  nominationId: string
  points: number
}

export interface ExportedPlaylistRanking extends Audit {
  id: string
  playlistId: string
  contributorId: string
  rankPosition: number
  correctGuesses: number
  totalGuesses: number
}

const audit = (item: Audit): Audit => ({ createdAt: item.createdAt, updatedAt: item.updatedAt, deletedAt: item.deletedAt })

const contributor = (c: ContributorItem): ExportedContributor => ({
  id: formatId('cont', c.id), name: c.name, email: c.email, avatarUrl: c.avatarUrl, ...audit(c),
})
const song = (s: SongItem): ExportedSong => ({
  id: formatId('song', s.id), artist: s.artist, name: s.name, album: s.album, releaseYear: s.releaseYear, url: s.url, ...audit(s),
})
const playlist = (p: PlaylistItem): ExportedPlaylist => ({
  id: formatId('play', p.id),
  name: p.name,
  description: p.description,
  status: p.status,
  ratingType: p.ratingType,
  leadContributorId: p.leadContributorId === undefined ? undefined : formatId('cont', p.leadContributorId),
  deadline: p.deadline,
  guessingDeadline: p.guessingDeadline,
  ...audit(p),
})
const nomination = (n: NominationItem): ExportedNomination => ({
  id: formatId('nom', n.id),
  playlistId: formatId('play', n.playlistId),
  songId: formatId('song', n.songId),
  nominatedById: formatId('cont', n.nominatedById),
  status: n.status,
  ...audit(n),
})
const guessSubmission = (g: GuessSubmissionItem): ExportedGuessSubmission => ({
  id: formatId('gsub', g.id), playlistId: formatId('play', g.playlistId), contributorId: formatId('cont', g.contributorId), ...audit(g),
})
const guess = (g: GuessItem): ExportedGuess => ({
  id: formatId('guess', g.id),
  playlistId: formatId('play', g.playlistId),
  nominationId: formatId('nom', g.nominationId),
  guesserId: formatId('cont', g.guesserId),
  guessedContributorId: formatId('cont', g.guessedContributorId),
  createdAt: g.createdAt,
})
const songRating = (r: SongRatingItem): ExportedSongRating => ({
  id: formatId('rate', r.id),
  playlistId: formatId('play', r.playlistId),
  contributorId: formatId('cont', r.contributorId),
  nominationId: formatId('nom', r.nominationId),
  points: r.points,
  ...audit(r),
})
const playlistRanking = (r: PlaylistRankingItem): ExportedPlaylistRanking => ({
  id: formatId('rank', r.id),
  playlistId: formatId('play', r.playlistId),
  contributorId: formatId('cont', r.contributorId),
  rankPosition: r.rankPosition,
  correctGuesses: r.correctGuesses,
  totalGuesses: r.totalGuesses,
  ...audit(r),
})

/** Stable order (oldest first) so two exports of the same tenant diff cleanly. */
const byCreatedAt = <T extends { createdAt: string; id: string }>(rows: T[]): T[] =>
  rows.sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id))

export async function exportOrganization(organizationId: number): Promise<OrganizationExport> {
  const org = await requireOrganization(organizationId)
  const tenantId = org.id

  // Eight independent partition reads — fan them out rather than await one after another.
  const [contributors, songs, playlists, nominations, guessSubmissions, guesses, songRatings, playlistRankings] =
    await Promise.all([
      findAllContributorsByTenant(tenantId),
      findAllSongsByTenant(tenantId),
      findAllPlaylistsByTenant(tenantId),
      findAllNominationsByTenant(tenantId),
      findAllGuessSubmissionsByTenant(tenantId),
      findAllGuessesByTenant(tenantId),
      findAllSongRatingsByTenant(tenantId),
      findAllRankingsByTenant(tenantId),
    ])

  const sections = {
    contributors: byCreatedAt(contributors.map(contributor)),
    songs: byCreatedAt(songs.map(song)),
    playlists: byCreatedAt(playlists.map(playlist)),
    nominations: byCreatedAt(nominations.map(nomination)),
    guessSubmissions: byCreatedAt(guessSubmissions.map(guessSubmission)),
    guesses: byCreatedAt(guesses.map(guess)),
    songRatings: byCreatedAt(songRatings.map(songRating)),
    playlistRankings: byCreatedAt(playlistRankings.map(playlistRanking)),
  }

  return {
    format: EXPORT_FORMAT,
    formatVersion: EXPORT_FORMAT_VERSION,
    exportedAt: new Date().toISOString(),
    organization: { id: org.id, slug: org.slug, name: org.name, allowedDomain: org.allowedDomain },
    ...sections,
    counts: Object.fromEntries(Object.entries(sections).map(([k, rows]) => [k, rows.length])) as OrganizationExport['counts'],
  }
}
