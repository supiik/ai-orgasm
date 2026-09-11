import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { publishPlaylist, type PublishPlaylistRequest } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  const request = await parseBody<PublishPlaylistRequest>(event)
  requireFields({ contributorId: request.contributorId })
  return publishPlaylist(ctx.tenantId!, playlistId, request)
})
