import { withAuth, pathSegment } from '../../lib/http'
import { getPlaylistById } from '../../lib/services/playlist'
import { NotFoundError } from '../../lib/errors'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const id = pathSegment(event, 0)
  const playlist = await getPlaylistById(ctx.tenantId!, id)
  if (!playlist) throw new NotFoundError(`Playlist not found: ${id}`)
  return playlist
})
