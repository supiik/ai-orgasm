import { withAuth } from '../../lib/http'
import { listOrganizationsForAdmin } from '../../lib/services/admin'

export const handler = withAuth('admin', 200, async () => listOrganizationsForAdmin())
