import { withAuth, pathSegment, parseBody } from '../../lib/http'
import { submitGuesses, type SubmitGuessesRequest } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 204, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  const request = await parseBody<SubmitGuessesRequest>(event)
  await submitGuesses(ctx.tenantId!, playlistId, ctx.contributorId!, request)
  return null
})
