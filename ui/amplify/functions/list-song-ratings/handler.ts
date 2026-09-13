import { withAuth, pathSegment } from '../../lib/http'
import { getSongRatings } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  return getSongRatings(ctx.tenantId!, playlistId)
})
