import { withAuth, parseBody, requireFields } from '../../lib/http'
import { createContributor, type CreateContributorRequest } from '../../lib/services/contributor'

export const handler = withAuth('authenticated-with-contributor', 201, async (event, ctx) => {
  const request = await parseBody<CreateContributorRequest>(event)
  requireFields({ name: request.name })
  return createContributor(ctx.tenantId!, request)
})
