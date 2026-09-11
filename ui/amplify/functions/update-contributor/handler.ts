import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { updateContributor, type UpdateContributorRequest } from '../../lib/services/contributor'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const id = pathSegment(event, 0)
  const request = await parseBody<UpdateContributorRequest>(event)
  requireFields({ name: request.name })
  return updateContributor(ctx.tenantId!, id, request)
})
