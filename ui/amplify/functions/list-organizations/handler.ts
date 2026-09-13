import { withAuth } from '../../lib/http'
import { listOrganizations } from '../../lib/services/organization'

// Any signed-in Cognito user — it feeds the org picker on the post-sign-up link form, which
// runs before a Contributor exists, so 'authenticated' (not '-with-contributor'). Was public
// until 2026-09-13; the org directory is not something anonymous callers need.
export const handler = withAuth('authenticated', 200, async () => listOrganizations())
