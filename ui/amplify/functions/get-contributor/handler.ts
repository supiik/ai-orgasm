import { withAuth, pathSegment } from '../../lib/http'
import { getContributorById } from '../../lib/services/contributor'
import { NotFoundError } from '../../lib/errors'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const id = pathSegment(event, 0)
  const contributor = await getContributorById(ctx.tenantId!, id)
  if (!contributor) throw new NotFoundError(`Contributor not found: ${id}`)
  return contributor
})
