import { withAuth, parseBody, requireFields } from '../../lib/http'
import { createSong, type CreateSongRequest } from '../../lib/services/song'

export const handler = withAuth('authenticated-with-contributor', 201, async (event, ctx) => {
  const request = await parseBody<CreateSongRequest>(event)
  requireFields({ artist: request.artist, name: request.name })
  return createSong(ctx.tenantId!, request)
})
