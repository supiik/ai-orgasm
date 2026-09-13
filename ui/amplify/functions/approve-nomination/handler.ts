import { withAuth, pathSegment } from '../../lib/http'
import { approveNomination } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) =>
  approveNomination(ctx.tenantId!, pathSegment(event, 1), ctx.contributorId!),
)
