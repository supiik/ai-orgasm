import { ConflictError, ForbiddenError, NotFoundError, ValidationError } from '../errors'
import { findAllContributorsByTenant, findContributorById } from '../repositories/contributor'
import { activateLicense, findLicenseByKey, findLicensesByOrganization, type LicenseItem } from '../repositories/license'

/**
 * Organization licences — the orgasm counterpart of a Sharpn school licence (organization ↔ school,
 * member ↔ student). The operator issues a key in orgasm-admin with a member cap and a validity
 * date; an org admin activates it for their organization. Rules:
 * - active until `validUntil` (inclusive), then GRACE_DAYS of grace (full access + a warning),
 *   then read-only: every non-GET request of a member is refused with 403;
 * - joining the organization (sign-up link, invite link, a contributor created by an org admin)
 *   is refused once the live member count reaches `maxMembers`;
 * - activating a new key always works, even when the current one expired.
 * The organization's licence is the most recently activated one.
 *
 * Enforcement is switched on per branch with LICENSE_ENFORCEMENT=on (Amplify env var): existing
 * organizations had no licence when this shipped, and would otherwise turn read-only on deploy.
 * With it off, status is still computed and shown, nothing is refused.
 */
export const GRACE_DAYS = 14
/** Key prefix — registered in aquarium (config/apps.ts licensePrefix), unique across products. */
export const LICENSE_PREFIX = 'ORG'
/** Same alphabet as Sharpn keys: digits 2-9 and letters without I, L, O. */
export const LICENSE_KEY = /^ORG(-[2-9ABCDEFGHJKMNPQRSTUVWXYZ]{4}){3}$/

/** Accepts `org aaaa-bbbb cccc` etc.; returns the canonical key or null. */
export function normLicenseKey(raw: unknown): string | null {
  if (typeof raw !== 'string') return null
  const c = raw.toUpperCase().replace(/[^0-9A-Z]/g, '')
  if (!c.startsWith(LICENSE_PREFIX) || c.length !== LICENSE_PREFIX.length + 12) return null
  const b = c.slice(LICENSE_PREFIX.length)
  const key = `${LICENSE_PREFIX}-${b.slice(0, 4)}-${b.slice(4, 8)}-${b.slice(8)}`
  return LICENSE_KEY.test(key) ? key : null
}

export type LicenseState = 'active' | 'grace' | 'expired' | 'none'

export interface LicenseStatusResponse {
  state: LicenseState
  enforced: boolean
  validUntil?: string
  /** Last day of the grace period, YYYY-MM-DD; set once validUntil has passed. */
  graceUntil?: string
  maxMembers?: number
  members: number
}

export const enforcementOn = () => process.env.LICENSE_ENFORCEMENT === 'on'
const today = () => new Date().toISOString().slice(0, 10)

export function addDays(day: string, days: number): string {
  const d = new Date(`${day}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

export function licenseState(lic: LicenseItem | undefined, day: string): LicenseState {
  if (!lic) return 'none'
  if (day <= lic.validUntil) return 'active'
  if (day <= addDays(lic.validUntil, GRACE_DAYS)) return 'grace'
  return 'expired'
}

export async function currentLicense(organizationId: number): Promise<LicenseItem | undefined> {
  return (await findLicensesByOrganization(organizationId))[0]
}

const liveMembers = async (tenantId: number) => (await findAllContributorsByTenant(tenantId)).filter((c) => !c.deletedAt).length

export async function getLicenseStatus(tenantId: number): Promise<LicenseStatusResponse> {
  const [lic, members] = await Promise.all([currentLicense(tenantId), liveMembers(tenantId)])
  const state = licenseState(lic, today())
  return {
    state,
    enforced: enforcementOn(),
    validUntil: lic?.validUntil,
    graceUntil: lic && state !== 'active' ? addDays(lic.validUntil, GRACE_DAYS) : undefined,
    maxMembers: lic?.maxMembers,
    members,
  }
}

/** withAuth calls this for every non-GET request of a member (unless the handler opts out). */
export async function assertWritable(tenantId: number): Promise<void> {
  if (!enforcementOn()) return
  const state = licenseState(await currentLicense(tenantId), today())
  if (state === 'none') throw new ForbiddenError('The organization has no licence — an org admin can activate one in organization settings')
  if (state === 'expired') throw new ForbiddenError('The organization licence has expired — read-only until a new licence is activated')
}

/** Before a new member joins the organization (link-contributor, create-contributor). */
export async function assertMemberCapacity(tenantId: number): Promise<void> {
  if (!enforcementOn()) return
  const [lic, members] = await Promise.all([currentLicense(tenantId), liveMembers(tenantId)])
  const state = licenseState(lic, today())
  if (!lic || state === 'none' || state === 'expired') throw new ForbiddenError('The organization has no valid licence')
  if (members >= lic.maxMembers) throw new ConflictError(`The organization licence allows at most ${lic.maxMembers} members`)
}

/** Org admin enters a key; works regardless of the current licence state. */
export async function activateOrganizationLicense(tenantId: number, actorId: bigint, rawKey: string): Promise<LicenseStatusResponse> {
  const actor = await findContributorById(tenantId, actorId)
  if (!actor?.orgAdmin) throw new ForbiddenError('Only an organization admin can activate a licence')
  const key = normLicenseKey(rawKey)
  if (!key) throw new ValidationError(['key: format ORG-XXXX-XXXX-XXXX'])
  const lic = await findLicenseByKey(key)
  if (!lic) throw new NotFoundError('Licence key not found')
  if (lic.organizationId !== undefined) {
    throw new ConflictError(lic.organizationId === tenantId ? 'This licence is already active for your organization' : 'This licence key has already been used')
  }
  if (!(await activateLicense(lic, tenantId, new Date().toISOString()))) {
    throw new ConflictError('This licence key has already been used')
  }
  return getLicenseStatus(tenantId)
}
