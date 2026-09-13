import { withAuth, parseBody, requireFields } from '../../lib/http'
import { createPlaylist, type CreatePlaylistRequest } from '../../lib/services/playlist'

export const handler = withAuth('authenticated-with-contributor', 201, async (event, ctx) => {
  const request = await parseBody<CreatePlaylistRequest>(event)
  requireFields({ name: request.name })
  return createPlaylist(ctx.tenantId!, request)
})
