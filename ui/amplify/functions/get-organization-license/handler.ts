import { withAuth } from '../../lib/http'
import { getLicenseStatus } from '../../lib/services/license'

// GET — the caller's organization licence: state, validity, member cap and count (any member).
export const handler = withAuth('authenticated-with-contributor', 200, async (_event, ctx) => getLicenseStatus(ctx.tenantId!))
