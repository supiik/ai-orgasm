import { PlaylistStatus } from '@orgasm/backend-client'

/**
 * Extracted from PlaylistDetailView.vue's action-button visibility conditions so the exact
 * rules (and past regressions in them) are unit-testable without mounting the component.
 */

/**
 * A brand-new playlist has no lead yet, so opening it can't be gated on already being the lead
 * — that condition can never be true before the playlist is opened, which is exactly the bug
 * this guarded against (see git history: "allow opening a brand-new playlist regardless of lead
 * contributor"). Any signed-in contributor may open a NEW playlist; doing so makes them the lead.
 */
export function canOpenPlaylist(status: PlaylistStatus | undefined): boolean {
  return status === PlaylistStatus.New
}

export function canStartGuessing(
  status: PlaylistStatus | undefined,
  isLead: boolean,
  deadlinePassed: boolean,
  allNominationsReviewed: boolean,
): boolean {
  return isLead && status === PlaylistStatus.Open && (deadlinePassed || allNominationsReviewed)
}

export function canPublish(status: PlaylistStatus | undefined, isLead: boolean): boolean {
  return isLead && status === PlaylistStatus.Guessing
}
