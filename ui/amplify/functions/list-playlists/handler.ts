import { withAuth, queryParam, pageable, pageBody } from '../../lib/http'
import { listPlaylists } from '../../lib/services/playlist'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const name = queryParam(event, 'name')
  const page = pageable(event)
  const { content, totalElements } = await listPlaylists(ctx.tenantId!, { name }, page)
  return pageBody(content, totalElements, page)
})
