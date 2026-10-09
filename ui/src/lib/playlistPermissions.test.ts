import { describe, expect, it } from 'vitest'
import { PlaylistStatus } from '@orgasm/backend-client'
import { canExportSpotify, canOpenPlaylist, canPublish, canStartGuessing } from './playlistPermissions'

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

describe('canExportSpotify', () => {
  it('requires the lead contributor', () => {
    expect(canExportSpotify(PlaylistStatus.Guessing, false)).toBe(false)
  })

  it('is not allowed while nominations are still open (New/Open)', () => {
    expect(canExportSpotify(PlaylistStatus.New, true)).toBe(false)
    expect(canExportSpotify(PlaylistStatus.Open, true)).toBe(false)
  })

  it('is allowed once the nomination phase has closed', () => {
    expect(canExportSpotify(PlaylistStatus.Guessing, true)).toBe(true)
    expect(canExportSpotify(PlaylistStatus.UnderEvaluation, true)).toBe(true)
    expect(canExportSpotify(PlaylistStatus.Closed, true)).toBe(true)
    expect(canExportSpotify(PlaylistStatus.Published, true)).toBe(true)
  })

  it('does not allow when the playlist has not loaded yet', () => {
    expect(canExportSpotify(undefined, true)).toBe(false)
  })
})
