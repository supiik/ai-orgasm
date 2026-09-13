import { withAuth } from '../../lib/http'
import { getRankings } from '../../lib/services/orgasm'

export const handler = withAuth('authenticated-with-contributor', 200, async (_event, ctx) => getRankings(ctx.tenantId!))
