import { withAuth, parseBody, requireFields } from '../../lib/http'
import { register, type RegisterContributorRequest } from '../../lib/services/registration'

export const handler = withAuth('public', 201, async (event) => {
  const request = await parseBody<RegisterContributorRequest>(event)
  requireFields({ organizationSlug: request.organizationSlug, name: request.name })
  return register(request)
})
