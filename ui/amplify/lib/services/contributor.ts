import { ForbiddenError, NotFoundError } from '../errors'
import { formatId, generateId, parseId } from '../idGenerator'
import type { PageRequest } from '../http'
import { findAllContributorsByTenant, findContributorById, saveContributor, type ContributorItem } from '../repositories/contributor'

export interface CreateContributorRequest {
  name: string
  email?: string
  avatarUrl?: string
}

export type UpdateContributorRequest = CreateContributorRequest

export interface ContributorResponse {
  id: string
  name: string
  email?: string
  avatarUrl?: string
  version?: number
  createdAt: string
  updatedAt: string
  /** Set by callers that already have the Organization at hand (e.g. get-current-contributor, link-contributor) — never populated by `toContributorResponse` itself. */
  organizationName?: string
  isOrgAdmin: boolean
}

export function toContributorResponse(item: ContributorItem): ContributorResponse {
  return {
    id: formatId('cont', item.id),
    name: item.name,
    email: item.email,
    avatarUrl: item.avatarUrl,
    version: item.version,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
    isOrgAdmin: item.orgAdmin ?? false,
  }
}

export async function createContributor(tenantId: number, request: CreateContributorRequest): Promise<ContributorResponse> {
  const id = generateId()
  const now = new Date().toISOString()
  const item: ContributorItem = {
    pk: `${tenantId}#CONTRIBUTOR`,
    sk: id.toString(),
    id,
    tenantId,
    name: request.name,
    email: request.email,
    avatarUrl: request.avatarUrl,
    createdAt: now,
    updatedAt: now,
  }
  return toContributorResponse(await saveContributor(item))
}

export async function getContributorById(tenantId: number, id: string): Promise<ContributorResponse | undefined> {
  const item = await findContributorById(tenantId, parseId(id))
  return item && !item.deletedAt ? toContributorResponse(item) : undefined
}

export async function requireContributorItem(tenantId: number, id: string): Promise<ContributorItem> {
  const item = await findContributorById(tenantId, parseId(id))
  if (!item || item.deletedAt) throw new NotFoundError(`Contributor not found: ${id}`)
  return item
}

export async function listContributors(
  tenantId: number,
  filter: { name?: string },
  page: PageRequest | undefined,
): Promise<{ content: ContributorResponse[]; totalElements: number }> {
  const needle = filter.name?.trim().toLowerCase()
  const all = (await findAllContributorsByTenant(tenantId))
    .filter((item) => !item.deletedAt)
    .filter((item) => !needle || item.name?.toLowerCase().includes(needle))

  const paged = page ? all.slice(page.page * page.size, page.page * page.size + page.size) : all
  return { content: paged.map(toContributorResponse), totalElements: all.length }
}

export async function updateContributor(tenantId: number, id: string, request: UpdateContributorRequest): Promise<ContributorResponse> {
  const existing = await requireContributorItem(tenantId, id)
  existing.name = request.name
  if (request.email !== undefined) existing.email = request.email
  if (request.avatarUrl !== undefined) existing.avatarUrl = request.avatarUrl
  existing.updatedAt = new Date().toISOString()
  return toContributorResponse(await saveContributor(existing))
}

export async function isOrgAdmin(tenantId: number, contributorId: bigint): Promise<boolean> {
  const item = await findContributorById(tenantId, contributorId)
  return !!item?.orgAdmin
}

export async function requireOrgAdmin(tenantId: number, contributorId: bigint): Promise<void> {
  if (!(await isOrgAdmin(tenantId, contributorId))) {
    throw new ForbiddenError('Only an organization admin can do this')
  }
}

/**
 * Self-service promote/demote: the acting contributor (`ctx.contributorId`, already resolved by
 * `withAuth` for 'authenticated-with-contributor' mode) must already be an org admin themselves.
 * The target is resolved by id through `findContributorById(tenantId, ...)`, so a cross-tenant id
 * simply 404s rather than needing an explicit same-org check here — mirrors backend-core's
 * ContributorService.setOrgAdmin.
 */
export async function setOrgAdmin(
  tenantId: number,
  actingContributorId: bigint,
  targetId: string,
  isOrgAdmin: boolean,
): Promise<ContributorResponse> {
  await requireOrgAdmin(tenantId, actingContributorId)
  const target = await requireContributorItem(tenantId, targetId)
  target.orgAdmin = isOrgAdmin
  target.updatedAt = new Date().toISOString()
  return toContributorResponse(await saveContributor(target))
}

export async function deleteContributor(tenantId: number, id: string): Promise<void> {
  const existing = await requireContributorItem(tenantId, id)
  existing.deletedAt = new Date().toISOString()
  await saveContributor(existing)
}
