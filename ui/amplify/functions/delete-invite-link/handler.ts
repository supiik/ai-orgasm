import { withAuth } from '../../lib/http'
import { revokeInviteLink } from '../../lib/services/organization'

export const handler = withAuth('authenticated-with-contributor', 204, async (_event, ctx) => {
  await revokeInviteLink(ctx.tenantId!, ctx.contributorId!)
  return null
})
