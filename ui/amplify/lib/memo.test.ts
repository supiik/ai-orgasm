import { describe, expect, it, vi } from 'vitest'
import { memoize } from './memo'

describe('memoize', () => {
  it('calls the underlying lookup once per distinct key', async () => {
    const lookup = vi.fn(async (id: bigint) => `row-${id}`)
    const memoized = memoize(lookup)

    const results = await Promise.all([memoized(1n), memoized(2n), memoized(1n), memoized(1n), memoized(2n)])

    expect(results).toEqual(['row-1', 'row-2', 'row-1', 'row-1', 'row-2'])
    expect(lookup).toHaveBeenCalledTimes(2)
  })

  it('dedupes concurrent in-flight lookups, not just settled ones', async () => {
    let resolve!: (v: string) => void
    const lookup = vi.fn(() => new Promise<string>((r) => (resolve = r)))
    const memoized = memoize(lookup)

    const a = memoized(7n)
    const b = memoized(7n)
    expect(lookup).toHaveBeenCalledTimes(1)

    resolve('row')
    expect(await a).toBe('row')
    expect(await b).toBe('row')
  })

  it('is scoped to one memoize() call, not shared across them', async () => {
    const lookup = vi.fn(async (id: bigint) => id)
    await memoize(lookup)(1n)
    await memoize(lookup)(1n)
    expect(lookup).toHaveBeenCalledTimes(2)
  })
})
