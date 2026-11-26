import { parseBody, requireFields, withAuth } from '../../lib/http'
import { activateOrganizationLicense } from '../../lib/services/license'

// POST {key} — org admin activates a licence key for their organization. Allowed even when the
// current licence has expired (that's when it's needed), hence allowWithoutLicense.
export const handler = withAuth(
  'authenticated-with-contributor',
  200,
  async (event, ctx) => {
    const { key } = await parseBody<{ key?: string }>(event)
    requireFields({ key })
    return activateOrganizationLicense(ctx.tenantId!, ctx.contributorId!, key!)
  },
  { allowWithoutLicense: true },
)
