import { beforeEach, describe, expect, it, vi } from 'vitest'
import { formatId } from '../idGenerator'
import type { PlaylistItem, PlaylistStatus } from '../repositories/playlist'
import type { ContributorItem } from '../repositories/contributor'
import type { SongItem } from '../repositories/song'
import type { NominationItem, NominationStatus } from '../repositories/nomination'
import type { GuessItem } from '../repositories/guess'
import type { PlaylistRankingItem } from '../repositories/playlistRanking'
import type { SongRatingItem } from '../repositories/songRating'

vi.mock('../repositories/playlist', () => ({
  findPlaylistById: vi.fn(),
  findAllPlaylistsByTenant: vi.fn(),
  savePlaylist: vi.fn(),
}))
vi.mock('../repositories/contributor', () => ({
  findContributorById: vi.fn(),
}))
vi.mock('../repositories/song', () => ({
  findSongById: vi.fn(),
}))
vi.mock('../repositories/nomination', () => ({
  findNominationById: vi.fn(),
  findNominationsByPlaylistId: vi.fn(),
  findNominationsBySongId: vi.fn(),
  saveNomination: vi.fn(),
  existsNominationForPlaylistAndSong: vi.fn(),
  declinePendingNominations: vi.fn(),
}))
vi.mock('../repositories/guess', () => ({
  findGuessesByPlaylistId: vi.fn(),
  saveGuess: vi.fn(),
  deleteGuessesByPlaylistAndGuesser: vi.fn(),
}))
vi.mock('../repositories/guessSubmission', () => ({
  existsGuessSubmission: vi.fn(),
  saveGuessSubmission: vi.fn(),
}))
vi.mock('../repositories/songRating', () => ({
  findSongRatingsByPlaylistId: vi.fn(),
  saveSongRating: vi.fn(),
  deleteSongRatingsByPlaylistAndContributor: vi.fn(),
}))
vi.mock('../repositories/playlistRanking', () => ({
  findAllRankingsByTenant: vi.fn(),
  saveRanking: vi.fn(),
}))

import * as playlistRepo from '../repositories/playlist'
import * as contributorRepo from '../repositories/contributor'
import * as songRepo from '../repositories/song'
import * as nominationRepo from '../repositories/nomination'
import * as guessRepo from '../repositories/guess'
import * as guessSubmissionRepo from '../repositories/guessSubmission'
import * as songRatingRepo from '../repositories/songRating'
import * as playlistRankingRepo from '../repositories/playlistRanking'
import {
  approveNomination,
  declineNomination,
  findNominationsBySong,
  getGuesses,
  getRankings,
  getSongRatings,
  nominateSong,
  openPlaylist,
  publishPlaylist,
  startGuessing,
  submitGuesses,
  submitRatings,
} from './orgasm'

const TENANT_ID = 1
const contId = (n: number | bigint) => formatId('cont', BigInt(n))
const songIdStr = (n: number | bigint) => formatId('song', BigInt(n))
const playIdStr = (n: number | bigint) => formatId('play', BigInt(n))
const nomIdStr = (n: number | bigint) => formatId('nom', BigInt(n))

function playlist(id: number, status: PlaylistStatus): PlaylistItem {
  return {
    pk: `${TENANT_ID}#PLAYLIST`,
    sk: id.toString(),
    id: BigInt(id),
    tenantId: TENANT_ID,
    name: 'My Mix',
    status,
    createdAt: 'now',
    updatedAt: 'now',
  }
}

function contributor(id: number): ContributorItem {
  return {
    pk: `${TENANT_ID}#CONTRIBUTOR`,
    sk: id.toString(),
    id: BigInt(id),
    tenantId: TENANT_ID,
    name: 'Ada',
    createdAt: 'now',
    updatedAt: 'now',
  }
}

function song(id: number): SongItem {
  return {
    pk: `${TENANT_ID}#SONG`,
    sk: id.toString(),
    id: BigInt(id),
    tenantId: TENANT_ID,
    artist: 'Artist',
    name: 'A Song',
    createdAt: 'now',
    updatedAt: 'now',
  }
}

function nomination(id: number, playlistId: number, songId: number, nominatedById: number, status: NominationStatus): NominationItem {
  return {
    pk: `${TENANT_ID}#NOMINATION`,
    sk: id.toString(),
    id: BigInt(id),
    tenantId: TENANT_ID,
    playlistId: BigInt(playlistId),
    songId: BigInt(songId),
    nominatedById: BigInt(nominatedById),
    status,
    createdAt: 'now',
    updatedAt: 'now',
  }
}

function guess(nominationId: number, guesserId: number, guessedContributorId: number): GuessItem {
  return {
    pk: `${TENANT_ID}#GUESS`,
    sk: '1',
    id: 1n,
    tenantId: TENANT_ID,
    playlistId: 1n,
    nominationId: BigInt(nominationId),
    guesserId: BigInt(guesserId),
    guessedContributorId: BigInt(guessedContributorId),
    createdAt: 'now',
  }
}

function rankingItem(playlistId: number, rank: number, contributorId: number): PlaylistRankingItem {
  return {
    pk: `${TENANT_ID}#PLAYLIST_RANKING`,
    sk: '1',
    id: 1n,
    tenantId: TENANT_ID,
    playlistId: BigInt(playlistId),
    contributorId: BigInt(contributorId),
    rankPosition: rank,
    correctGuesses: rank === 1 ? 2 : 1,
    totalGuesses: 2,
    createdAt: 'now',
    updatedAt: 'now',
  }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('openPlaylist', () => {
  it('succeeds when NEW', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'NEW'))
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))
    vi.mocked(playlistRepo.savePlaylist).mockImplementation(async (item) => item)

    const deadline = new Date(Date.now() + 60_000).toISOString()
    const result = await openPlaylist(TENANT_ID, playIdStr(1), { contributorId: contId(2), deadline })

    expect(result.status).toBe('OPEN')
    const saved = vi.mocked(playlistRepo.savePlaylist).mock.calls[0][0]
    expect(saved.status).toBe('OPEN')
    expect(saved.leadContributorId).toBe(2n)
    expect(saved.deadline).toBe(deadline)
  })

  it('throws when not NEW', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'OPEN'))

    await expect(
      openPlaylist(TENANT_ID, playIdStr(1), { contributorId: contId(2), deadline: new Date().toISOString() }),
    ).rejects.toThrow(/NEW/)
  })

  it('throws when contributor missing', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'NEW'))
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(undefined)

    await expect(
      openPlaylist(TENANT_ID, playIdStr(1), { contributorId: contId(2), deadline: new Date().toISOString() }),
    ).rejects.toThrow(/Contributor not found/)
  })
})

describe('nominateSong', () => {
  it('succeeds when OPEN', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'OPEN'))
    vi.mocked(songRepo.findSongById).mockResolvedValue(song(3))
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))
    vi.mocked(nominationRepo.existsNominationForPlaylistAndSong).mockResolvedValue(false)
    vi.mocked(nominationRepo.saveNomination).mockImplementation(async (item) => item)

    const response = await nominateSong(TENANT_ID, playIdStr(1), { contributorId: contId(2), songId: songIdStr(3) })

    expect(response.status).toBe('PENDING')
    expect(response.playlistId).toBe(playIdStr(1))
  })

  it('throws when not OPEN', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'NEW'))

    await expect(
      nominateSong(TENANT_ID, playIdStr(1), { contributorId: contId(2), songId: songIdStr(3) }),
    ).rejects.toThrow(/OPEN/)
  })

  it('throws when deadline passed', async () => {
    const p = playlist(1, 'OPEN')
    p.deadline = new Date(Date.now() - 60_000).toISOString()
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)

    await expect(
      nominateSong(TENANT_ID, playIdStr(1), { contributorId: contId(2), songId: songIdStr(3) }),
    ).rejects.toThrow(/deadline/)
  })

  it('throws when already nominated', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'OPEN'))
    vi.mocked(songRepo.findSongById).mockResolvedValue(song(3))
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))
    vi.mocked(nominationRepo.existsNominationForPlaylistAndSong).mockResolvedValue(true)

    await expect(
      nominateSong(TENANT_ID, playIdStr(1), { contributorId: contId(2), songId: songIdStr(3) }),
    ).rejects.toThrow(/already been nominated/)
  })
})

describe('approve/declineNomination', () => {
  it('approves when pending and reviewer is lead', async () => {
    const nom = nomination(10, 1, 3, 2, 'PENDING')
    const p = playlist(1, 'OPEN')
    p.leadContributorId = 2n
    vi.mocked(nominationRepo.findNominationById).mockResolvedValue(nom)
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)
    vi.mocked(nominationRepo.saveNomination).mockImplementation(async (item) => item)

    const response = await approveNomination(TENANT_ID, nomIdStr(10), { reviewerId: contId(2) })

    expect(response.status).toBe('APPROVED')
  })

  it('throws declining when not pending', async () => {
    vi.mocked(nominationRepo.findNominationById).mockResolvedValue(nomination(10, 1, 3, 2, 'APPROVED'))

    await expect(declineNomination(TENANT_ID, nomIdStr(10), { reviewerId: contId(2) })).rejects.toThrow(/pending/)
  })

  it('throws when reviewer is not lead', async () => {
    const nom = nomination(10, 1, 3, 2, 'PENDING')
    const p = playlist(1, 'OPEN')
    p.leadContributorId = 99n
    vi.mocked(nominationRepo.findNominationById).mockResolvedValue(nom)
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)

    await expect(approveNomination(TENANT_ID, nomIdStr(10), { reviewerId: contId(2) })).rejects.toThrow(/lead contributor/)
  })
})

describe('startGuessing', () => {
  it('succeeds when no pending nominations', async () => {
    const p = playlist(1, 'OPEN')
    p.leadContributorId = 2n
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)
    vi.mocked(nominationRepo.findNominationsByPlaylistId).mockResolvedValue([nomination(10, 1, 3, 2, 'APPROVED')])
    vi.mocked(nominationRepo.declinePendingNominations).mockResolvedValue(0)
    vi.mocked(playlistRepo.savePlaylist).mockImplementation(async (item) => item)

    await startGuessing(TENANT_ID, playIdStr(1), { contributorId: contId(2) })

    const saved = vi.mocked(playlistRepo.savePlaylist).mock.calls[0][0]
    expect(saved.status).toBe('GUESSING')
    expect(saved.guessingDeadline).toBeDefined()
  })

  it('throws when deadline not passed and pending nominations exist', async () => {
    const p = playlist(1, 'OPEN')
    p.leadContributorId = 2n
    p.deadline = new Date(Date.now() + 3_600_000).toISOString()
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)
    vi.mocked(nominationRepo.findNominationsByPlaylistId).mockResolvedValue([nomination(10, 1, 3, 2, 'PENDING')])

    await expect(startGuessing(TENANT_ID, playIdStr(1), { contributorId: contId(2) })).rejects.toThrow()
  })

  it('throws when not lead contributor', async () => {
    const p = playlist(1, 'OPEN')
    p.leadContributorId = 99n
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)

    await expect(startGuessing(TENANT_ID, playIdStr(1), { contributorId: contId(2) })).rejects.toThrow(/lead contributor/)
  })
})

describe('submitGuesses', () => {
  it('deletes then recreates', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'GUESSING'))
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))
    vi.mocked(nominationRepo.findNominationById).mockResolvedValue(nomination(10, 1, 3, 5, 'APPROVED'))
    vi.mocked(guessSubmissionRepo.existsGuessSubmission).mockResolvedValue(false)

    await submitGuesses(TENANT_ID, playIdStr(1), {
      contributorId: contId(2),
      guesses: [{ nominationId: nomIdStr(10), guessedContributorId: contId(5) }],
    })

    expect(guessRepo.deleteGuessesByPlaylistAndGuesser).toHaveBeenCalledWith(1n, 2n)
    expect(guessRepo.saveGuess).toHaveBeenCalledTimes(1)
    expect(guessSubmissionRepo.saveGuessSubmission).toHaveBeenCalledTimes(1)
  })

  it('throws when not GUESSING', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'OPEN'))

    await expect(submitGuesses(TENANT_ID, playIdStr(1), { contributorId: contId(2), guesses: [] })).rejects.toThrow(/GUESSING/)
  })
})

describe('publishPlaylist / ranking computation', () => {
  it('computes standard competition ranking', async () => {
    const p = playlist(1, 'GUESSING')
    p.leadContributorId = 2n
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)
    vi.mocked(playlistRepo.savePlaylist).mockImplementation(async (item) => item)
    vi.mocked(nominationRepo.findNominationsByPlaylistId).mockResolvedValue([
      nomination(10, 1, 3, 100, 'APPROVED'),
      nomination(11, 1, 4, 101, 'APPROVED'),
    ])

    const g1 = guess(10, 200, 100) // correct
    const g2 = guess(11, 200, 101) // correct
    const g3 = guess(10, 201, 999) // wrong
    const g4 = guess(11, 201, 101) // correct
    vi.mocked(guessRepo.findGuessesByPlaylistId).mockResolvedValue([g1, g2, g3, g4])
    vi.mocked(playlistRankingRepo.saveRanking).mockImplementation(async (item) => item)

    await publishPlaylist(TENANT_ID, playIdStr(1), { contributorId: contId(2) })

    expect(playlistRankingRepo.saveRanking).toHaveBeenCalledTimes(2)
    const saves = vi.mocked(playlistRankingRepo.saveRanking).mock.calls.map((c) => c[0])
    const byContributor = new Map(saves.map((r) => [r.contributorId, r]))

    expect(byContributor.get(200n)?.correctGuesses).toBe(2)
    expect(byContributor.get(200n)?.rankPosition).toBe(1)
    expect(byContributor.get(201n)?.correctGuesses).toBe(1)
    expect(byContributor.get(201n)?.totalGuesses).toBe(2)
    expect(byContributor.get(201n)?.rankPosition).toBe(2)
  })

  it('throws when not GUESSING', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'OPEN'))

    await expect(publishPlaylist(TENANT_ID, playIdStr(1), { contributorId: contId(2) })).rejects.toThrow(/GUESSING/)
  })
})

describe('submitRatings', () => {
  it('succeeds with a valid LINEAR point set', async () => {
    const p = playlist(1, 'PUBLISHED')
    p.ratingType = 'LINEAR'
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))
    vi.mocked(nominationRepo.findNominationById).mockImplementation(async (_tenantId, id) =>
      nomination(Number(id), 1, 3, 999, 'APPROVED'),
    )

    await submitRatings(TENANT_ID, playIdStr(1), {
      contributorId: contId(2),
      ratings: [
        { nominationId: nomIdStr(10), points: 1 },
        { nominationId: nomIdStr(11), points: 2 },
        { nominationId: nomIdStr(12), points: 3 },
      ],
    })

    expect(songRatingRepo.deleteSongRatingsByPlaylistAndContributor).toHaveBeenCalledWith(1n, 2n)
    expect(songRatingRepo.saveSongRating).toHaveBeenCalledTimes(3)
  })

  it('throws when point set does not match rating type', async () => {
    const p = playlist(1, 'PUBLISHED')
    p.ratingType = 'LINEAR'
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))

    await expect(
      submitRatings(TENANT_ID, playIdStr(1), {
        contributorId: contId(2),
        ratings: [
          { nominationId: nomIdStr(10), points: 1 },
          { nominationId: nomIdStr(11), points: 1 },
        ],
      }),
    ).rejects.toThrow(/Ratings must be exactly/)

    expect(songRatingRepo.deleteSongRatingsByPlaylistAndContributor).not.toHaveBeenCalled()
  })

  it('throws when rating own nomination', async () => {
    const p = playlist(1, 'PUBLISHED')
    p.ratingType = 'BEST_SONG'
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(p)
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))
    vi.mocked(nominationRepo.findNominationById).mockResolvedValue(nomination(10, 1, 3, 2, 'APPROVED'))

    await expect(
      submitRatings(TENANT_ID, playIdStr(1), {
        contributorId: contId(2),
        ratings: [{ nominationId: nomIdStr(10), points: 1 }],
      }),
    ).rejects.toThrow(/own nomination/)
  })

  it('throws when not PUBLISHED', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'GUESSING'))

    await expect(
      submitRatings(TENANT_ID, playIdStr(1), { contributorId: contId(2), ratings: [{ nominationId: nomIdStr(10), points: 1 }] }),
    ).rejects.toThrow(/PUBLISHED/)
  })
})

describe('reads', () => {
  it('getGuesses maps to response', async () => {
    vi.mocked(guessRepo.findGuessesByPlaylistId).mockResolvedValue([guess(10, 200, 100)])

    const result = await getGuesses(playIdStr(1))

    expect(result).toHaveLength(1)
    expect(result[0].nominationId).toBe(nomIdStr(10))
  })

  it('findNominationsBySong hydrates playlist/contributor names', async () => {
    vi.mocked(nominationRepo.findNominationsBySongId).mockResolvedValue([nomination(10, 1, 3, 2, 'APPROVED')])
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'OPEN'))
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))

    const result = await findNominationsBySong(TENANT_ID, songIdStr(3))

    expect(result).toHaveLength(1)
    expect(result[0].playlistName).toBe('My Mix')
    expect(result[0].nominatedByName).toBe('Ada')
  })

  it('getRankings sorts by playlist then rank', async () => {
    const r1 = rankingItem(1, 1, 200)
    const r2 = rankingItem(1, 2, 201)
    vi.mocked(playlistRankingRepo.findAllRankingsByTenant).mockResolvedValue([r2, r1])
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist(1, 'PUBLISHED'))
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(200))

    const result = await getRankings(TENANT_ID)

    expect(result).toHaveLength(2)
    expect(result[0].rankPosition).toBe(1)
    expect(result[1].rankPosition).toBe(2)
  })

  it('getSongRatings maps to response', async () => {
    const r: SongRatingItem = {
      pk: `${TENANT_ID}#SONG_RATING`,
      sk: '1',
      id: 1n,
      tenantId: TENANT_ID,
      playlistId: 1n,
      nominationId: 10n,
      contributorId: 2n,
      points: 3,
      createdAt: 'now',
      updatedAt: 'now',
    }
    vi.mocked(songRatingRepo.findSongRatingsByPlaylistId).mockResolvedValue([r])
    vi.mocked(contributorRepo.findContributorById).mockResolvedValue(contributor(2))

    const result = await getSongRatings(TENANT_ID, playIdStr(1))

    expect(result).toHaveLength(1)
    expect(result[0].points).toBe(3)
    expect(result[0].contributorName).toBe('Ada')
  })
})
