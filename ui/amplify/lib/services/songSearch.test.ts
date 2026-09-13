import { describe, expect, it, vi } from 'vitest'
import { ValidationError } from '../errors'
import type { SongSearchProvider } from '../songSearch'
import { DEFAULT_LIMIT, MAX_LIMIT, searchSongs } from './songSearch'

function fakeProvider(): SongSearchProvider & { search: ReturnType<typeof vi.fn> } {
  return { name: 'fake', search: vi.fn(async () => []) }
}

describe('searchSongs', () => {
  it('trims the query and applies the default limit', async () => {
    const p = fakeProvider()
    await searchSongs('  creep ', undefined, p)
    expect(p.search).toHaveBeenCalledWith('creep', DEFAULT_LIMIT)
  })

  it('caps the limit', async () => {
    const p = fakeProvider()
    await searchSongs('creep', 999, p)
    expect(p.search).toHaveBeenCalledWith('creep', MAX_LIMIT)
  })

  it('returns whatever the provider returns', async () => {
    const p = fakeProvider()
    const hit = { source: 'fake', externalId: '1', artist: 'A', name: 'B' }
    p.search.mockResolvedValueOnce([hit])
    expect(await searchSongs('creep', 5, p)).toEqual([hit])
  })

  it.each([undefined, '', ' ', 'a'])('rejects a missing/too-short query (%j) without calling the provider', async (q) => {
    const p = fakeProvider()
    await expect(searchSongs(q, undefined, p)).rejects.toBeInstanceOf(ValidationError)
    expect(p.search).not.toHaveBeenCalled()
  })

  it('rejects an over-long query', async () => {
    await expect(searchSongs('x'.repeat(201), undefined, fakeProvider())).rejects.toThrow(/at most 200/)
  })

  it.each([0, -1, 1.5, Number.NaN])('rejects a non-positive-integer limit (%s)', async (limit) => {
    await expect(searchSongs('creep', limit, fakeProvider())).rejects.toThrow(/limit must be a positive integer/)
  })
})
