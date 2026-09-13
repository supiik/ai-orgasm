import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { updateSong, type UpdateSongRequest } from '../../lib/services/song'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const id = pathSegment(event, 0)
  const request = await parseBody<UpdateSongRequest>(event)
  requireFields({ artist: request.artist, name: request.name })
  return updateSong(ctx.tenantId!, id, request)
})
