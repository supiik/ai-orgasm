import { withAuth, pathSegment } from '../../lib/http'
import { exportOrganization } from '../../lib/services/export'

// GET /{id}/export
export const handler = withAuth('admin', 200, async (event) => exportOrganization(Number(pathSegment(event, 1))))
