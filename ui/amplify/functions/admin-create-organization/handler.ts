import { withAuth, parseBody, requireFields } from '../../lib/http'
import { createOrganization, type CreateOrganizationRequest } from '../../lib/services/admin'

export const handler = withAuth('admin', 201, async (event) => {
  const request = await parseBody<CreateOrganizationRequest>(event)
  requireFields({ slug: request.slug, name: request.name })
  return createOrganization(request)
})
