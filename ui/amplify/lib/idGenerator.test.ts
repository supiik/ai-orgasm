import { describe, expect, it } from 'vitest'
import { formatId, generateId, parseId } from './idGenerator'

// Golden values generated from the actual Java bit-manipulation logic (IdGenerator.java's
// Long.reverse/XOR), with ID_GENERATOR_SECRET unset (secretKey=0) — see the plan's note on
// verifying the BigInt port against known Java-produced id/format pairs.
const GOLDEN_ZERO_SECRET: [bigint, string][] = [
  [0n, 'test-0000000000000000'],
  [1n, 'test-8000000000000000'],
  [12345n, 'test-9c0c000000000000'],
  [123456789n, 'test-a8b3dae000000000'],
  [9007199254740993n, 'test-8000000000000400'], // beyond JS's safe-integer range as a plain number
  [9223372036854775807n, 'test-fffffffffffffffe'], // Long.MAX_VALUE
]

describe('formatId / parseId (secretKey=0)', () => {
  it.each(GOLDEN_ZERO_SECRET)('formats %s as %s, matching the Java implementation exactly', (id, expected) => {
    expect(formatId('test', id)).toBe(expected)
  })

  it.each(GOLDEN_ZERO_SECRET)('parses %s back from %s', (id, formatted) => {
    expect(parseId(formatted)).toBe(id)
  })
})

describe('generateId', () => {
  it('produces a positive, sortable id that grows over time', async () => {
    const a = generateId()
    await new Promise((resolve) => setTimeout(resolve, 2))
    const b = generateId()

    expect(a).toBeGreaterThan(0n)
    expect(b).toBeGreaterThan(a)
  })

  it('round-trips through format/parse', () => {
    const id = generateId()
    expect(parseId(formatId('play', id))).toBe(id)
  })
})
