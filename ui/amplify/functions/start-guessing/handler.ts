import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { startGuessing, type StartGuessingRequest } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  const request = await parseBody<StartGuessingRequest>(event)
  requireFields({ contributorId: request.contributorId })
  return startGuessing(ctx.tenantId!, playlistId, request)
})
