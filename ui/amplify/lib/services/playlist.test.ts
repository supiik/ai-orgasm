import { beforeEach, describe, expect, it, vi } from 'vitest'
import { formatId } from '../idGenerator'
import type { PlaylistItem, PlaylistStatus } from '../repositories/playlist'
import { ConflictError, ValidationError } from '../errors'

vi.mock('../repositories/playlist', () => ({
  findPlaylistById: vi.fn(),
  findAllPlaylistsByTenant: vi.fn(),
  savePlaylist: vi.fn(),
}))
vi.mock('../repositories/contributor', () => ({
  findContributorById: vi.fn(),
}))

import * as playlistRepo from '../repositories/playlist'
import * as contributorRepo from '../repositories/contributor'
import { createPlaylist, updatePlaylist } from './playlist'

const TENANT_ID = 1
const LEAD = 7n
const OTHER = 8n
const playIdStr = (n: number) => formatId('play', BigInt(n))

function playlist(status: PlaylistStatus, leadContributorId: bigint | undefined = LEAD): PlaylistItem {
  return {
    pk: `${TENANT_ID}#PLAYLIST`,
    sk: '1',
    id: 1n,
    tenantId: TENANT_ID,
    name: 'My Mix',
    status,
    leadContributorId,
    deadline: '2026-01-01T00:00:00.000Z',
    guessingDeadline: '2026-01-08T00:00:00.000Z',
    maxNominationsPerContributor: 1,
    createdAt: 'now',
    updatedAt: 'now',
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(playlistRepo.savePlaylist).mockImplementation(async (item) => item)
  vi.mocked(contributorRepo.findContributorById).mockResolvedValue(undefined)
})

describe('updatePlaylist', () => {
  it('updates name/description without touching deadlines', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('NEW', undefined))

    const result = await updatePlaylist(TENANT_ID, playIdStr(1), OTHER, { name: 'Renamed', description: 'd' })

    expect(result.name).toBe('Renamed')
    expect(result.description).toBe('d')
    expect(result.deadline).toBe('2026-01-01T00:00:00.000Z')
    expect(result.guessingDeadline).toBe('2026-01-08T00:00:00.000Z')
  })

  it('lets the lead extend the nomination deadline while OPEN', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('OPEN'))
    const future = new Date(Date.now() + 86_400_000).toISOString()

    const result = await updatePlaylist(TENANT_ID, playIdStr(1), LEAD, { name: 'My Mix', deadline: future })

    expect(result.deadline).toBe(future)
    expect(result.guessingDeadline).toBe('2026-01-08T00:00:00.000Z')
  })

  it('lets the lead extend the guessing deadline while GUESSING', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('GUESSING'))
    const future = new Date(Date.now() + 2 * 86_400_000).toISOString()

    const result = await updatePlaylist(TENANT_ID, playIdStr(1), LEAD, { name: 'My Mix', guessingDeadline: future })

    expect(result.guessingDeadline).toBe(future)
    expect(result.deadline).toBe('2026-01-01T00:00:00.000Z')
  })

  it('rejects a deadline change that moves it into the past', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('OPEN'))
    const past = new Date(Date.now() - 86_400_000).toISOString()

    await expect(updatePlaylist(TENANT_ID, playIdStr(1), LEAD, { name: 'My Mix', deadline: past }))
      .rejects.toThrow(ValidationError)
  })

  it('rejects a nomination deadline change from a non-lead', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('OPEN'))

    await expect(updatePlaylist(TENANT_ID, playIdStr(1), OTHER, { name: 'My Mix', deadline: '2026-02-01T00:00:00Z' }))
      .rejects.toThrow(ConflictError)
    expect(playlistRepo.savePlaylist).not.toHaveBeenCalled()
  })

  it('rejects a nomination deadline change when the playlist is not OPEN', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('GUESSING'))

    await expect(updatePlaylist(TENANT_ID, playIdStr(1), LEAD, { name: 'My Mix', deadline: '2026-02-01T00:00:00Z' }))
      .rejects.toThrow(/must be OPEN/)
  })

  it('rejects a guessing deadline change when the playlist is not GUESSING', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('OPEN'))

    await expect(updatePlaylist(TENANT_ID, playIdStr(1), LEAD, { name: 'My Mix', guessingDeadline: '2026-03-01T00:00:00Z' }))
      .rejects.toThrow(/must be GUESSING/)
  })

  it('rejects an unparseable deadline', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('OPEN'))

    await expect(updatePlaylist(TENANT_ID, playIdStr(1), LEAD, { name: 'My Mix', deadline: 'next tuesday' }))
      .rejects.toThrow(ValidationError)
  })

  it('rejects a maxNominationsPerContributor above 3', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('NEW', undefined))

    await expect(updatePlaylist(TENANT_ID, playIdStr(1), OTHER, { name: 'My Mix', maxNominationsPerContributor: 4 }))
      .rejects.toThrow(ValidationError)
  })

  it('accepts a maxNominationsPerContributor of 3', async () => {
    vi.mocked(playlistRepo.findPlaylistById).mockResolvedValue(playlist('NEW', undefined))

    const result = await updatePlaylist(TENANT_ID, playIdStr(1), OTHER, { name: 'My Mix', maxNominationsPerContributor: 3 })

    expect(result.maxNominationsPerContributor).toBe(3)
  })
})

describe('createPlaylist', () => {
  it('defaults maxNominationsPerContributor to 1 when not provided', async () => {
    const result = await createPlaylist(TENANT_ID, { name: 'My Mix' })

    expect(result.maxNominationsPerContributor).toBe(1)
  })

  it('accepts an explicit maxNominationsPerContributor up to 3', async () => {
    const result = await createPlaylist(TENANT_ID, { name: 'My Mix', maxNominationsPerContributor: 3 })

    expect(result.maxNominationsPerContributor).toBe(3)
  })

  it('rejects a maxNominationsPerContributor above 3', async () => {
    await expect(createPlaylist(TENANT_ID, { name: 'My Mix', maxNominationsPerContributor: 4 }))
      .rejects.toThrow(ValidationError)
  })
})
