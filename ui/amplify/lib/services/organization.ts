import { randomUUID } from 'node:crypto'
import { NotFoundError } from '../errors'
import { requireOrgAdmin } from './contributor'
import {
  findAllOrganizations,
  findOrganizationById,
  findOrganizationByInviteToken,
  findOrganizationBySlug,
  saveOrganization,
  type OrganizationItem,
} from '../repositories/organization'

export interface OrganizationResponse {
  id: number
  slug: string
  name: string
}

export interface InviteLinkResponse {
  inviteToken: string | null
}

function toOrganizationResponse(item: OrganizationItem): OrganizationResponse {
  return { id: item.id, slug: item.slug, name: item.name }
}

export async function listOrganizations(): Promise<OrganizationResponse[]> {
  const items = await findAllOrganizations()
  return items.map(toOrganizationResponse)
}

export function findOrganizationBySlugOrUndefined(slug: string): Promise<OrganizationItem | undefined> {
  return findOrganizationBySlug(slug)
}

/** Public lookup used by the invite-redemption UI to show "You're joining X" before the caller commits. */
export async function getOrganizationByInviteToken(inviteToken: string): Promise<OrganizationResponse> {
  const org = await findOrganizationByInviteToken(inviteToken)
  if (!org) throw new NotFoundError('Invalid or expired invite link')
  return toOrganizationResponse(org)
}

async function requireOwnOrganization(tenantId: number): Promise<OrganizationItem> {
  const org = await findOrganizationById(tenantId)
  if (!org) throw new NotFoundError(`Organization not found: ${tenantId}`)
  return org
}

export async function getInviteLink(tenantId: number, actingContributorId: bigint): Promise<InviteLinkResponse> {
  await requireOrgAdmin(tenantId, actingContributorId)
  const org = await requireOwnOrganization(tenantId)
  return { inviteToken: org.inviteToken ?? null }
}

/** Generating a new token invalidates any previously shared link — see OrganizationItem.inviteToken. */
export async function createInviteLink(tenantId: number, actingContributorId: bigint): Promise<InviteLinkResponse> {
  await requireOrgAdmin(tenantId, actingContributorId)
  const org = await requireOwnOrganization(tenantId)
  org.inviteToken = randomUUID().replace(/-/g, '')
  await saveOrganization(org)
  return { inviteToken: org.inviteToken }
}

export async function revokeInviteLink(tenantId: number, actingContributorId: bigint): Promise<void> {
  await requireOrgAdmin(tenantId, actingContributorId)
  const org = await requireOwnOrganization(tenantId)
  delete org.inviteToken
  await saveOrganization(org)
}
