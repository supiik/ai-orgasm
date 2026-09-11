import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { declineNomination, type ReviewNominationRequest } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const nominationId = pathSegment(event, 1)
  const request = await parseBody<ReviewNominationRequest>(event)
  requireFields({ reviewerId: request.reviewerId })
  return declineNomination(ctx.tenantId!, nominationId, request)
})
