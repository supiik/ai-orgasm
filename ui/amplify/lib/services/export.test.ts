import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../repositories/organization', () => ({ findOrganizationById: vi.fn() }))
vi.mock('../repositories/contributor', () => ({ findAllContributorsByTenant: vi.fn() }))
vi.mock('../repositories/song', () => ({ findAllSongsByTenant: vi.fn() }))
vi.mock('../repositories/playlist', () => ({ findAllPlaylistsByTenant: vi.fn() }))
vi.mock('../repositories/nomination', () => ({ findAllNominationsByTenant: vi.fn() }))
vi.mock('../repositories/guessSubmission', () => ({ findAllGuessSubmissionsByTenant: vi.fn() }))
vi.mock('../repositories/guess', () => ({ findAllGuessesByTenant: vi.fn() }))
vi.mock('../repositories/songRating', () => ({ findAllSongRatingsByTenant: vi.fn() }))
vi.mock('../repositories/playlistRanking', () => ({ findAllRankingsByTenant: vi.fn() }))

import * as organizationRepo from '../repositories/organization'
import * as contributorRepo from '../repositories/contributor'
import * as songRepo from '../repositories/song'
import * as playlistRepo from '../repositories/playlist'
import * as nominationRepo from '../repositories/nomination'
import * as guessSubmissionRepo from '../repositories/guessSubmission'
import * as guessRepo from '../repositories/guess'
import * as songRatingRepo from '../repositories/songRating'
import * as rankingRepo from '../repositories/playlistRanking'
import { NotFoundError } from '../errors'
import { formatId } from '../idGenerator'
import { EXPORT_FORMAT, EXPORT_FORMAT_VERSION, exportOrganization } from './export'

const T = 1
const at = (day: number) => `2026-01-${String(day).padStart(2, '0')}T00:00:00Z`
const audit = (day: number) => ({ createdAt: at(day), updatedAt: at(day) })

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue({ id: T, slug: 'acme', name: 'ACME', allowedDomain: 'acme.com' })
  vi.mocked(contributorRepo.findAllContributorsByTenant).mockResolvedValue([])
  vi.mocked(songRepo.findAllSongsByTenant).mockResolvedValue([])
  vi.mocked(playlistRepo.findAllPlaylistsByTenant).mockResolvedValue([])
  vi.mocked(nominationRepo.findAllNominationsByTenant).mockResolvedValue([])
  vi.mocked(guessSubmissionRepo.findAllGuessSubmissionsByTenant).mockResolvedValue([])
  vi.mocked(guessRepo.findAllGuessesByTenant).mockResolvedValue([])
  vi.mocked(songRatingRepo.findAllSongRatingsByTenant).mockResolvedValue([])
  vi.mocked(rankingRepo.findAllRankingsByTenant).mockResolvedValue([])
})

describe('exportOrganization', () => {
  it('404s for an unknown or malformed organization id', async () => {
    vi.mocked(organizationRepo.findOrganizationById).mockResolvedValue(undefined)
    await expect(exportOrganization(42)).rejects.toBeInstanceOf(NotFoundError)
    await expect(exportOrganization(Number.NaN)).rejects.toBeInstanceOf(NotFoundError)
    expect(contributorRepo.findAllContributorsByTenant).not.toHaveBeenCalled()
  })

  it('describes itself and the organization, with every section present even when empty', async () => {
    const doc = await exportOrganization(T)
    expect(doc.format).toBe(EXPORT_FORMAT)
    expect(doc.formatVersion).toBe(EXPORT_FORMAT_VERSION)
    expect(Date.parse(doc.exportedAt)).not.toBeNaN()
    expect(doc.organization).toEqual({ id: T, slug: 'acme', name: 'ACME', allowedDomain: 'acme.com' })
    expect(doc.counts).toEqual({
      contributors: 0, songs: 0, playlists: 0, nominations: 0, guessSubmissions: 0, guesses: 0, songRatings: 0, playlistRankings: 0,
    })
    for (const key of Object.keys(doc.counts)) expect(doc[key as keyof typeof doc.counts]).toEqual([])
    // Every repository is queried for the resolved tenant, not the raw path argument.
    for (const fn of [
      contributorRepo.findAllContributorsByTenant, songRepo.findAllSongsByTenant, playlistRepo.findAllPlaylistsByTenant,
      nominationRepo.findAllNominationsByTenant, guessSubmissionRepo.findAllGuessSubmissionsByTenant, guessRepo.findAllGuessesByTenant,
      songRatingRepo.findAllSongRatingsByTenant, rankingRepo.findAllRankingsByTenant,
    ]) expect(fn).toHaveBeenCalledWith(T)
  })

  it('uses external ids for keys and foreign keys, drops storage/lock/identity fields, keeps deletedAt', async () => {
    vi.mocked(contributorRepo.findAllContributorsByTenant).mockResolvedValue([
      { pk: '1#CONTRIBUTOR', sk: '10', id: 10n, tenantId: T, name: 'Alice', email: 'alice@acme.com', cognitoSub: 'sub-1', version: 3, ...audit(1) },
      { pk: '1#CONTRIBUTOR', sk: '11', id: 11n, tenantId: T, name: 'Bob', version: 1, deletedAt: at(5), ...audit(2) },
    ])
    vi.mocked(songRepo.findAllSongsByTenant).mockResolvedValue([
      { pk: '1#SONG', sk: '20', id: 20n, tenantId: T, artist: 'Radiohead', name: 'Creep', album: 'Pablo Honey', releaseYear: 1993, version: 1, ...audit(1) },
    ])
    vi.mocked(playlistRepo.findAllPlaylistsByTenant).mockResolvedValue([
      { pk: '1#PLAYLIST', sk: '30', id: 30n, tenantId: T, name: 'Mix', status: 'PUBLISHED', ratingType: 'TOP3', leadContributorId: 10n, deadline: at(3), version: 7, ...audit(1) },
      { pk: '1#PLAYLIST', sk: '31', id: 31n, tenantId: T, name: 'Draft', status: 'DRAFT', version: 1, ...audit(2) },
    ])
    vi.mocked(nominationRepo.findAllNominationsByTenant).mockResolvedValue([
      { pk: '1#NOMINATION', sk: '40', id: 40n, tenantId: T, playlistId: 30n, songId: 20n, nominatedById: 10n, status: 'APPROVED', version: 2, ...audit(2) },
    ])
    vi.mocked(guessSubmissionRepo.findAllGuessSubmissionsByTenant).mockResolvedValue([
      { pk: '1#GUESS_SUBMISSION', sk: '50', id: 50n, tenantId: T, playlistId: 30n, contributorId: 11n, version: 1, ...audit(3) },
    ])
    vi.mocked(guessRepo.findAllGuessesByTenant).mockResolvedValue([
      { pk: '1#GUESS', sk: '60', id: 60n, tenantId: T, playlistId: 30n, nominationId: 40n, guesserId: 11n, guessedContributorId: 10n, createdAt: at(3) },
    ])
    vi.mocked(songRatingRepo.findAllSongRatingsByTenant).mockResolvedValue([
      { pk: '1#SONG_RATING', sk: '70', id: 70n, tenantId: T, playlistId: 30n, contributorId: 11n, nominationId: 40n, points: 3, version: 1, ...audit(4) },
    ])
    vi.mocked(rankingRepo.findAllRankingsByTenant).mockResolvedValue([
      { pk: '1#PLAYLIST_RANKING', sk: '80', id: 80n, tenantId: T, playlistId: 30n, contributorId: 11n, rankPosition: 1, correctGuesses: 1, totalGuesses: 1, version: 1, ...audit(4) },
    ])

    const doc = await exportOrganization(T)
    const alice = formatId('cont', 10n), bob = formatId('cont', 11n)
    const creep = formatId('song', 20n), mix = formatId('play', 30n), nom = formatId('nom', 40n)

    expect(doc.contributors).toEqual([
      { id: alice, name: 'Alice', email: 'alice@acme.com', avatarUrl: undefined, ...audit(1), deletedAt: undefined },
      { id: bob, name: 'Bob', email: undefined, avatarUrl: undefined, ...audit(2), deletedAt: at(5) },
    ])
    expect(doc.songs).toEqual([{ id: creep, artist: 'Radiohead', name: 'Creep', album: 'Pablo Honey', releaseYear: 1993, url: undefined, ...audit(1), deletedAt: undefined }])
    expect(doc.playlists[0]).toMatchObject({ id: mix, status: 'PUBLISHED', ratingType: 'TOP3', leadContributorId: alice, deadline: at(3) })
    expect(doc.playlists[1]).toMatchObject({ id: formatId('play', 31n), status: 'DRAFT', leadContributorId: undefined })
    expect(doc.nominations).toEqual([{ id: nom, playlistId: mix, songId: creep, nominatedById: alice, status: 'APPROVED', ...audit(2), deletedAt: undefined }])
    expect(doc.guessSubmissions).toEqual([{ id: formatId('gsub', 50n), playlistId: mix, contributorId: bob, ...audit(3), deletedAt: undefined }])
    expect(doc.guesses).toEqual([{ id: formatId('guess', 60n), playlistId: mix, nominationId: nom, guesserId: bob, guessedContributorId: alice, createdAt: at(3) }])
    expect(doc.songRatings).toEqual([{ id: formatId('rate', 70n), playlistId: mix, contributorId: bob, nominationId: nom, points: 3, ...audit(4), deletedAt: undefined }])
    expect(doc.playlistRankings).toEqual([{ id: formatId('rank', 80n), playlistId: mix, contributorId: bob, rankPosition: 1, correctGuesses: 1, totalGuesses: 1, ...audit(4), deletedAt: undefined }])
    expect(doc.counts).toEqual({ contributors: 2, songs: 1, playlists: 2, nominations: 1, guessSubmissions: 1, guesses: 1, songRatings: 1, playlistRankings: 1 })

    // Nothing internal leaks, and the whole thing survives JSON.stringify (no bigint left behind).
    const json = JSON.stringify(doc)
    for (const leaked of ['"pk"', '"sk"', '"version"', '"tenantId"', 'cognitoSub', 'sub-1']) expect(json).not.toContain(leaked)
  })

  it('orders every section oldest-first, tie-breaking on id', async () => {
    vi.mocked(songRepo.findAllSongsByTenant).mockResolvedValue([
      { pk: '1#SONG', sk: '3', id: 3n, tenantId: T, artist: 'c', name: 'c', ...audit(3) },
      { pk: '1#SONG', sk: '1', id: 1n, tenantId: T, artist: 'a', name: 'a', ...audit(1) },
      { pk: '1#SONG', sk: '2', id: 2n, tenantId: T, artist: 'b', name: 'b', ...audit(1) },
    ])
    const doc = await exportOrganization(T)
    const expected = [formatId('song', 1n), formatId('song', 2n)].sort()
    expect(doc.songs.map((s) => s.id)).toEqual([...expected, formatId('song', 3n)])
  })
})
