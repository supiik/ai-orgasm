import { withAuth, parseBody, pathSegment, requireFields } from '../../lib/http'
import { addOrganizationContributor, type AddOrganizationContributorRequest } from '../../lib/services/admin'

// POST /{id}/contributors
export const handler = withAuth('admin', 201, async (event) => {
  const id = Number(pathSegment(event, 1))
  const request = await parseBody<AddOrganizationContributorRequest>(event)
  requireFields({ name: request.name, email: request.email })
  return addOrganizationContributor(id, request)
})
