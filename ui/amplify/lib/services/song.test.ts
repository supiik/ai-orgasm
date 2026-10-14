import { beforeEach, describe, expect, it, vi } from 'vitest'
import { formatId } from '../idGenerator'
import type { SongItem } from '../repositories/song'
import type { NominationItem } from '../repositories/nomination'
import { ConflictError, NotFoundError } from '../errors'

vi.mock('../repositories/song', () => ({
  findSongById: vi.fn(),
  findAllSongsByTenant: vi.fn(),
  saveSong: vi.fn(),
}))
vi.mock('../repositories/nomination', () => ({
  findNominationsBySongId: vi.fn(),
}))

import * as songRepo from '../repositories/song'
import * as nominationRepo from '../repositories/nomination'
import { getSongById, updateSong } from './song'

const TENANT_ID = 1
const songIdStr = (n: number) => formatId('song', BigInt(n))

function song(): SongItem {
  return {
    pk: `${TENANT_ID}#SONG`,
    sk: '1',
    id: 1n,
    tenantId: TENANT_ID,
    artist: 'Old Artist',
    name: 'Old Name',
    createdAt: 'now',
    updatedAt: 'now',
  }
}

function nomination(overrides: Partial<NominationItem> = {}): NominationItem {
  return {
    pk: `${TENANT_ID}#NOMINATION`,
    sk: '10',
    id: 10n,
    tenantId: TENANT_ID,
    playlistId: 2n,
    songId: 1n,
    nominatedById: 3n,
    status: 'PENDING',
    createdAt: 'now',
    updatedAt: 'now',
    ...overrides,
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(songRepo.saveSong).mockImplementation(async (item) => item)
})

describe('getSongById', () => {
  it('sets hasApprovedNomination when an approved nomination exists', async () => {
    vi.mocked(songRepo.findSongById).mockResolvedValue(song())
    vi.mocked(nominationRepo.findNominationsBySongId).mockResolvedValue([nomination({ status: 'APPROVED' })])

    const result = await getSongById(TENANT_ID, songIdStr(1))

    expect(result?.hasApprovedNomination).toBe(true)
  })

  it('leaves hasApprovedNomination false when nominations are only pending', async () => {
    vi.mocked(songRepo.findSongById).mockResolvedValue(song())
    vi.mocked(nominationRepo.findNominationsBySongId).mockResolvedValue([nomination({ status: 'PENDING' })])

    const result = await getSongById(TENANT_ID, songIdStr(1))

    expect(result?.hasApprovedNomination).toBe(false)
  })
})

describe('updateSong', () => {
  it('updates the song when it has never been nominated', async () => {
    vi.mocked(songRepo.findSongById).mockResolvedValue(song())
    vi.mocked(nominationRepo.findNominationsBySongId).mockResolvedValue([])

    const result = await updateSong(TENANT_ID, songIdStr(1), { artist: 'New Artist', name: 'New Name' })

    expect(result.artist).toBe('New Artist')
    expect(result.name).toBe('New Name')
  })

  it('rejects an edit once an approved nomination references the song', async () => {
    vi.mocked(songRepo.findSongById).mockResolvedValue(song())
    vi.mocked(nominationRepo.findNominationsBySongId).mockResolvedValue([nomination({ status: 'APPROVED' })])

    await expect(updateSong(TENANT_ID, songIdStr(1), { artist: 'New Artist', name: 'New Name' }))
      .rejects.toThrow(ConflictError)
    expect(songRepo.saveSong).not.toHaveBeenCalled()
  })

  it('allows editing while the song only has pending or declined nominations', async () => {
    vi.mocked(songRepo.findSongById).mockResolvedValue(song())
    vi.mocked(nominationRepo.findNominationsBySongId).mockResolvedValue([
      nomination({ status: 'PENDING' }),
      nomination({ id: 11n, status: 'DECLINED' }),
    ])

    const result = await updateSong(TENANT_ID, songIdStr(1), { artist: 'New Artist', name: 'New Name' })

    expect(result.artist).toBe('New Artist')
  })

  it('ignores soft-deleted nominations when deciding whether the song is locked', async () => {
    vi.mocked(songRepo.findSongById).mockResolvedValue(song())
    vi.mocked(nominationRepo.findNominationsBySongId).mockResolvedValue([nomination({ status: 'APPROVED', deletedAt: 'now' })])

    const result = await updateSong(TENANT_ID, songIdStr(1), { artist: 'New Artist', name: 'New Name' })

    expect(result.artist).toBe('New Artist')
  })

  it('throws not found before checking nominations when the song does not exist', async () => {
    vi.mocked(songRepo.findSongById).mockResolvedValue(undefined)

    await expect(updateSong(TENANT_ID, songIdStr(1), { artist: 'X', name: 'Y' }))
      .rejects.toThrow(NotFoundError)
    expect(nominationRepo.findNominationsBySongId).not.toHaveBeenCalled()
  })
})
