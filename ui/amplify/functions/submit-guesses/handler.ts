import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { submitGuesses, type SubmitGuessesRequest } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 204, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  const request = await parseBody<SubmitGuessesRequest>(event)
  requireFields({ contributorId: request.contributorId })
  await submitGuesses(ctx.tenantId!, playlistId, request)
  return null
})
