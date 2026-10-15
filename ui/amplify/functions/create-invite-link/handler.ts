import { withAuth } from '../../lib/http'
import { createInviteLink } from '../../lib/services/organization'

export const handler = withAuth('authenticated-with-contributor', 200, async (_event, ctx) => {
  return createInviteLink(ctx.tenantId!, ctx.contributorId!)
})
