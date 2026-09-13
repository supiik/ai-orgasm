import { withAuth, parseBody, pathSegment, requireFields } from '../../lib/http'
import { updateOrganization, type UpdateOrganizationRequest } from '../../lib/services/admin'

// PUT /{id}
export const handler = withAuth('admin', 200, async (event) => {
  const id = Number(pathSegment(event, 0))
  const request = await parseBody<UpdateOrganizationRequest>(event)
  requireFields({ name: request.name })
  return updateOrganization(id, request)
})
