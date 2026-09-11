import { findAllOrganizations, findOrganizationBySlug, type OrganizationItem } from '../repositories/organization'

export interface OrganizationResponse {
  id: number
  slug: string
  name: string
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
