import { describe, expect, it } from 'vitest'
import { parseReleaseYear } from './releaseYear'

describe('parseReleaseYear', () => {
  it('parses a plain digit string', () => {
    expect(parseReleaseYear('1993')).toBe(1993)
  })

  it('returns undefined for an empty string', () => {
    expect(parseReleaseYear('')).toBeUndefined()
  })

  it('returns undefined for a whitespace-only string', () => {
    expect(parseReleaseYear('   ')).toBeUndefined()
  })

  it('handles an already-numeric value — regression for the bug where Vue auto-casts ' +
    'v-model on <input type="number"> to a JS number, and calling .trim() on it directly threw ' +
    'a TypeError that silently failed every song create/update with a year filled in', () => {
    expect(parseReleaseYear(1983)).toBe(1983)
  })

  it('treats numeric 0 as a real value, not empty', () => {
    expect(parseReleaseYear(0)).toBe(0)
  })
})
