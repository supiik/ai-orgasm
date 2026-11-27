// Suspension of an organization from aquarium's CRM (orgasm-admin writes OrganizationItem.suspendedAt).
// Checked on every member request (lib/http.ts withAuth) and before anyone joins (link-contributor).
import { SuspendedError } from '../errors'
import { findOrganizationById } from '../repositories/organization'

export async function assertNotSuspended(tenantId: number): Promise<void> {
  const org = await findOrganizationById(tenantId)
  if (org?.suspendedAt) throw new SuspendedError()
}
