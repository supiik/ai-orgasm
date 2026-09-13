import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { submitRatings, type SubmitRatingsRequest } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 204, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  const request = await parseBody<SubmitRatingsRequest>(event)
  requireFields({ ratings: request.ratings })
  await submitRatings(ctx.tenantId!, playlistId, ctx.contributorId!, request)
  return null
})
