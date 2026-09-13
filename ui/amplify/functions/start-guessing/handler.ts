import { withAuth, pathSegment } from '../../lib/http'
import { startGuessing } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) =>
  startGuessing(ctx.tenantId!, pathSegment(event, 1), ctx.contributorId!),
)
