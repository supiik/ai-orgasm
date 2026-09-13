import { describe, expect, it } from 'vitest'
import { PlaylistStatus } from '@orgasm/backend-client'
import { canOpenPlaylist, canPublish, canStartGuessing } from './playlistPermissions'

describe('canOpenPlaylist', () => {
  it('allows opening a NEW playlist with no lead yet — regression for the bug where this was gated on isLead, which can never be true before a playlist is opened', () => {
    expect(canOpenPlaylist(PlaylistStatus.New)).toBe(true)
  })

  it('does not allow opening a playlist that is already open or further along', () => {
    expect(canOpenPlaylist(PlaylistStatus.Open)).toBe(false)
    expect(canOpenPlaylist(PlaylistStatus.Guessing)).toBe(false)
    expect(canOpenPlaylist(PlaylistStatus.Published)).toBe(false)
  })

  it('does not allow opening when the playlist has not loaded yet', () => {
    expect(canOpenPlaylist(undefined)).toBe(false)
  })
})

describe('canStartGuessing', () => {
  it('requires the lead contributor', () => {
    expect(canStartGuessing(PlaylistStatus.Open, false, true, true)).toBe(false)
  })

  it('requires the playlist to be Open', () => {
    expect(canStartGuessing(PlaylistStatus.New, true, true, true)).toBe(false)
    expect(canStartGuessing(PlaylistStatus.Guessing, true, true, true)).toBe(false)
  })

  it('requires either the deadline to have passed or all nominations reviewed', () => {
    expect(canStartGuessing(PlaylistStatus.Open, true, false, false)).toBe(false)
    expect(canStartGuessing(PlaylistStatus.Open, true, true, false)).toBe(true)
    expect(canStartGuessing(PlaylistStatus.Open, true, false, true)).toBe(true)
  })
})

describe('canPublish', () => {
  it('requires the lead contributor and Guessing status', () => {
    expect(canPublish(PlaylistStatus.Guessing, true)).toBe(true)
    expect(canPublish(PlaylistStatus.Guessing, false)).toBe(false)
    expect(canPublish(PlaylistStatus.Open, true)).toBe(false)
  })
})
