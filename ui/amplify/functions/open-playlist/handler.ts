import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { openPlaylist, type OpenPlaylistRequest } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  const request = await parseBody<OpenPlaylistRequest>(event)
  requireFields({ deadline: request.deadline })
  return openPlaylist(ctx.tenantId!, playlistId, ctx.contributorId!, request)
})
