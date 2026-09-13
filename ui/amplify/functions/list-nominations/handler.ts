import { withAuth, pathSegment, pageable, pageBody } from '../../lib/http'
import { findNominations } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const playlistId = pathSegment(event, 1)
  const page = pageable(event)
  const { content, totalElements } = await findNominations(ctx.tenantId!, playlistId, page)
  return pageBody(content, totalElements, page)
})
