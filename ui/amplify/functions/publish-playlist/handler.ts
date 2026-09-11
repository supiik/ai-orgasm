import { withAuth, pathSegment } from '../../lib/http'
import { publishPlaylist } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) =>
  publishPlaylist(ctx.tenantId!, pathSegment(event, 1), ctx.contributorId!),
)
