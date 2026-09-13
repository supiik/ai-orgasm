import { withAuth, queryParam, pageable, pageBody } from '../../lib/http'
import { listContributors } from '../../lib/services/contributor'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const name = queryParam(event, 'name')
  const page = pageable(event)
  const { content, totalElements } = await listContributors(ctx.tenantId!, { name }, page)
  return pageBody(content, totalElements, page)
})
