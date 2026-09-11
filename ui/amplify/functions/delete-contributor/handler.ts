import { withAuth, pathSegment } from '../../lib/http'
import { deleteContributor } from '../../lib/services/contributor'

export const handler = withAuth('authenticated-with-contributor', 204, async (event, ctx) => {
  await deleteContributor(ctx.tenantId!, pathSegment(event, 0))
  return null
})
