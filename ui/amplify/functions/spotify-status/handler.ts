import { withAuth } from '../../lib/http'
import { getConnectionStatus } from '../../lib/services/spotify'

/** GET — whether the calling contributor has a connected Spotify account. */
export const handler = withAuth('authenticated-with-contributor', 200, async (_event, ctx) => {
  return getConnectionStatus(ctx.tenantId!, ctx.contributorId!)
})
