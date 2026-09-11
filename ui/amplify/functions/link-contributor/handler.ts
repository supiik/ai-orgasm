import { withAuth, parseBody, requireFields } from '../../lib/http'
import { linkContributor, type LinkContributorRequest } from '../../lib/services/registration'

export const handler = withAuth('authenticated', 200, async (event, ctx) => {
  const request = await parseBody<LinkContributorRequest>(event)
  requireFields({ organizationSlug: request.organizationSlug, name: request.name })
  return linkContributor(request, ctx.cognitoSub!, ctx.cognitoEmail!)
})
