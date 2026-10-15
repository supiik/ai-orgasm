import { withAuth } from '../../lib/http'
import { getInviteLink } from '../../lib/services/organization'

export const handler = withAuth('authenticated-with-contributor', 200, async (_event, ctx) => {
  return getInviteLink(ctx.tenantId!, ctx.contributorId!)
})
