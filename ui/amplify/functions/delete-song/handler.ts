import { withAuth, pathSegment } from '../../lib/http'
import { deleteSong } from '../../lib/services/song'

export const handler = withAuth('authenticated-with-contributor', 204, async (event, ctx) => {
  await deleteSong(ctx.tenantId!, pathSegment(event, 0))
  return null
})
