import { withAuth, pathSegment } from '../../lib/http'
import { deletePlaylist } from '../../lib/services/playlist'

export const handler = withAuth('authenticated-with-contributor', 204, async (event, ctx) => {
  await deletePlaylist(ctx.tenantId!, pathSegment(event, 0))
  return null
})
