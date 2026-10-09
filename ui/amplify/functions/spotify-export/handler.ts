import { withAuth, pathSegment } from '../../lib/http'
import { exportPlaylistToSpotify } from '../../lib/services/spotify'

/** POST /api/v1/spotify/playlists/{id}/export — lead-only, see CLAUDE.md "Spotify playlist export". */
export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  return exportPlaylistToSpotify(ctx.tenantId!, playlistId, ctx.contributorId!)
})
