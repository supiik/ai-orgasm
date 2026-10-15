import { withAuth, pathSegment } from '../../lib/http'
import { getOrganizationByInviteToken } from '../../lib/services/organization'

// 'authenticated' (not 'authenticated-with-contributor'): this is reached right after sign-up,
// before the caller necessarily has a linked Contributor yet — same reasoning as link-contributor.
export const handler = withAuth('authenticated', 200, async (event) => {
  return getOrganizationByInviteToken(pathSegment(event, 0))
})
