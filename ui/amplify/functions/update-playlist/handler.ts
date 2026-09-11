import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { updatePlaylist, type UpdatePlaylistRequest } from '../../lib/services/playlist'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const id = pathSegment(event, 0)
  const request = await parseBody<UpdatePlaylistRequest>(event)
  requireFields({ name: request.name })
  return updatePlaylist(ctx.tenantId!, id, request)
})
