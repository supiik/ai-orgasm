import { withAuth } from '../../lib/http'
import { getContributorById } from '../../lib/services/contributor'
import { formatId } from '../../lib/idGenerator'
import { NotFoundError } from '../../lib/errors'

export const handler = withAuth('authenticated-with-contributor', 200, async (_event, ctx) => {
  const contributor = await getContributorById(ctx.tenantId!, formatId('cont', ctx.contributorId!))
  if (!contributor) throw new NotFoundError('Contributor not found')
  return contributor
})
