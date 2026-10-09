import { withAuth, parseBody, requireFields } from '../../lib/http'
import { handleSpotifyCallback } from '../../lib/services/spotify'

interface SpotifyCallbackRequest {
  code: string
  state: string
}

/** POST { code, state } — exchanges the Spotify authorization code and stores the connection. */
export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const request = await parseBody<SpotifyCallbackRequest>(event)
  requireFields({ code: request.code, state: request.state })
  return handleSpotifyCallback(ctx.tenantId!, ctx.contributorId!, request.code, request.state)
})
