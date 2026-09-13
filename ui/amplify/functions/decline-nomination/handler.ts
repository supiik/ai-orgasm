import { withAuth, pathSegment } from '../../lib/http'
import { declineNomination } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (event, ctx) =>
  declineNomination(ctx.tenantId!, pathSegment(event, 1), ctx.contributorId!),
)
