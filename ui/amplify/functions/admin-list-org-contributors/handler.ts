import { withAuth, pathSegment } from '../../lib/http'
import { listOrganizationContributors } from '../../lib/services/admin'

// GET /{id}/contributors
export const handler = withAuth('admin', 200, async (event) => listOrganizationContributors(Number(pathSegment(event, 1))))
