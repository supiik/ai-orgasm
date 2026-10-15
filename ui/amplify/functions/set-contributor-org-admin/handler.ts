import { withAuth, pathSegment, parseBody, requireFields } from '../../lib/http'
import { setOrgAdmin } from '../../lib/services/contributor'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const id = pathSegment(event, 1)
  const request = await parseBody<{ isOrgAdmin: boolean }>(event)
  requireFields({ isOrgAdmin: request.isOrgAdmin })
  return setOrgAdmin(ctx.tenantId!, ctx.contributorId!, id, request.isOrgAdmin)
})
