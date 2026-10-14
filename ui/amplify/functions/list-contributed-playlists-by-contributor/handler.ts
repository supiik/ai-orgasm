import { withAuth, pathSegment, pageable, pageBody } from '../../lib/http'
import { findPlaylistsContributedByContributor } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) => {
  const contributorId = pathSegment(event, 1)
  const page = pageable(event)
  const { content, totalElements } = await findPlaylistsContributedByContributor(ctx.tenantId!, contributorId, page)
  return pageBody(content, totalElements, page)
})
