import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ValidationError } from '../errors'
import { mintSpotifyState, verifySpotifyState } from './state'

describe('spotify state', () => {
  beforeEach(() => {
    process.env.SPOTIFY_STATE_SECRET = '00112233445566778899aabbccddeeff00112233445566778899aabbccddee'
  })

  afterEach(() => {
    vi.useRealTimers()
    delete process.env.SPOTIFY_STATE_SECRET
  })

  it('round-trips a playlist id', () => {
    const state = mintSpotifyState('play-abc123')
    expect(verifySpotifyState(state)).toBe('play-abc123')
  })

  it('round-trips an absent playlist id as undefined', () => {
    const state = mintSpotifyState(undefined)
    expect(verifySpotifyState(state)).toBeUndefined()
  })

  it('rejects a tampered payload', () => {
    const state = mintSpotifyState('play-abc123')
    const raw = Buffer.from(state, 'base64url').toString('utf8')
    const tampered = Buffer.from(raw.replace('play-abc123', 'play-evil999'), 'utf8').toString('base64url')
    expect(() => verifySpotifyState(tampered)).toThrow(ValidationError)
  })

  it('rejects garbage input', () => {
    expect(() => verifySpotifyState('not-a-valid-state')).toThrow(ValidationError)
  })

  it('rejects a state signed with a different secret', () => {
    const state = mintSpotifyState('play-abc123')
    process.env.SPOTIFY_STATE_SECRET = 'ffeeddccbbaa998877665544332211000102030405060708090a0b0c0d0e0f'
    expect(() => verifySpotifyState(state)).toThrow(ValidationError)
  })

  it('rejects an expired state', () => {
    vi.useFakeTimers()
    const state = mintSpotifyState('play-abc123')
    vi.advanceTimersByTime(11 * 60 * 1000)
    expect(() => verifySpotifyState(state)).toThrow(ValidationError)
  })
})
