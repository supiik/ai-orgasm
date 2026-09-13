import { withAuth, pathSegment } from '../../lib/http'
import { findNominationsBySong } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const songId = pathSegment(event, 1)
  return findNominationsBySong(ctx.tenantId!, songId)
})
