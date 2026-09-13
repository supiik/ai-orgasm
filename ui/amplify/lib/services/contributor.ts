import { NotFoundError } from '../errors'
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
  const all = (await findAllContributorsByTenant(tenantId))
    .filter((item) => !item.deletedAt)
    .filter((item) => !filter.name || item.name?.toLowerCase().includes(filter.name.toLowerCase()))

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

export async function deleteContributor(tenantId: number, id: string): Promise<void> {
  const existing = await requireContributorItem(tenantId, id)
  existing.deletedAt = new Date().toISOString()
  await saveContributor(existing)
}
