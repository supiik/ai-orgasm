import { withAuth } from '../../lib/http'
import { listOrganizations } from '../../lib/services/organization'

export const handler = withAuth('public', 200, async () => listOrganizations())
