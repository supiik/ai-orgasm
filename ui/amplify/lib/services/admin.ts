import { ConflictError, NotFoundError, ValidationError } from '../errors'
import { findCognitoSubByEmail } from '../cognito'
import { generateId } from '../idGenerator'
import {
  findAllContributorsByTenant,
  findContributorByCognitoSub,
  findContributorByEmail,
  saveContributor,
  type ContributorItem,
} from '../repositories/contributor'
import {
  findAllOrganizations,
  findOrganizationById,
  findOrganizationBySlug,
  insertOrganization,
  saveOrganization,
  type OrganizationItem,
} from '../repositories/organization'
import { assertEmailAllowed } from './registration'
import { toContributorResponse, type ContributorResponse } from './contributor'

/**
 * Cross-tenant administration behind the `admin` AuthMode (lib/http.ts). No Java counterpart:
 * the Spring backend still has Organizations created straight in the billing database (see
 * CLAUDE.md "Public registration endpoints"). Everything here takes the organization
 * explicitly — there is no tenant context on an admin request.
 */

/** Unlike the public `OrganizationResponse`, includes the registration gate — admins configure it. */
export interface AdminOrganizationResponse {
  id: number
  slug: string
  name: string
  allowedDomain?: string
}

export interface CreateOrganizationRequest {
  slug: string
  name: string
  allowedDomain?: string
}

export interface UpdateOrganizationRequest {
  name: string
  allowedDomain?: string
}

/** Contributor as an admin sees it: plus whether a Cognito account is attached yet. */
export interface AdminContributorResponse extends ContributorResponse {
  linked: boolean
}

export interface AddOrganizationContributorRequest {
  name: string
  email: string
  avatarUrl?: string
}

const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const DOMAIN_PATTERN = /^[a-z0-9]+(?:[-.][a-z0-9]+)*\.[a-z]{2,}$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const MAX_ID_RETRIES = 3

function toAdminOrganizationResponse(item: OrganizationItem): AdminOrganizationResponse {
  return { id: item.id, slug: item.slug, name: item.name, allowedDomain: item.allowedDomain }
}

function toAdminContributorResponse(item: ContributorItem): AdminContributorResponse {
  return { ...toContributorResponse(item), linked: Boolean(item.cognitoSub) }
}

/** Accepts `example.com` or `@example.com`; empty/blank means "no restriction" → undefined. */
function normalizeAllowedDomain(raw: string | undefined): string | undefined {
  const trimmed = raw?.trim().replace(/^@/, '').toLowerCase()
  if (!trimmed) return undefined
  if (!DOMAIN_PATTERN.test(trimmed)) {
    throw new ValidationError(['allowedDomain: must be a domain name such as example.com'])
  }
  return trimmed
}

export async function listOrganizationsForAdmin(): Promise<AdminOrganizationResponse[]> {
  const items = await findAllOrganizations()
  return items.sort((a, b) => a.id - b.id).map(toAdminOrganizationResponse)
}

export async function requireOrganization(id: number): Promise<OrganizationItem> {
  const org = Number.isInteger(id) && id > 0 ? await findOrganizationById(id) : undefined
  if (!org) throw new NotFoundError(`Organization not found: ${id}`)
  return org
}

/**
 * Ids are `max(existing) + 1` off a Scan of the (small, global) directory table, made safe
 * against two admins creating at once by the insert's `attribute_not_exists(id)` condition —
 * on a collision the loser recomputes and retries. Slug uniqueness is only pre-checked (the
 * `bySlug` GSI can't enforce it), same convention as `findOrganizationBySlug` documents.
 */
export async function createOrganization(request: CreateOrganizationRequest): Promise<AdminOrganizationResponse> {
  const slug = request.slug.trim().toLowerCase()
  const name = request.name.trim()
  if (!SLUG_PATTERN.test(slug)) {
    throw new ValidationError(['slug: lowercase letters, digits and single hyphens only (e.g. my-org)'])
  }
  const allowedDomain = normalizeAllowedDomain(request.allowedDomain)
  if (await findOrganizationBySlug(slug)) {
    throw new ConflictError(`Organization slug already in use: ${slug}`)
  }

  for (let attempt = 0; attempt < MAX_ID_RETRIES; attempt++) {
    const existing = await findAllOrganizations()
    const id = existing.reduce((max, o) => Math.max(max, o.id), 0) + 1
    const item: OrganizationItem = { id, slug, name, allowedDomain }
    if (await insertOrganization(item)) return toAdminOrganizationResponse(item)
  }
  throw new ConflictError('Could not allocate an organization id; please retry')
}

export async function updateOrganization(id: number, request: UpdateOrganizationRequest): Promise<AdminOrganizationResponse> {
  const org = await requireOrganization(id)
  org.name = request.name.trim()
  org.allowedDomain = normalizeAllowedDomain(request.allowedDomain)
  return toAdminOrganizationResponse(await saveOrganization(org))
}

export async function listOrganizationContributors(organizationId: number): Promise<AdminContributorResponse[]> {
  const org = await requireOrganization(organizationId)
  const items = await findAllContributorsByTenant(org.id)
  return items
    .filter((c) => !c.deletedAt)
    .sort((a, b) => a.name.localeCompare(b.name))
    .map(toAdminContributorResponse)
}

/**
 * Manually places a user in an organization. Creates the Contributor with the given email,
 * then — if that email already has a Cognito account — attaches it right away, so the user is
 * fully linked on their next sign-in. If they have not signed up yet, `linkContributor` picks
 * the record up by email match when they do (the existing self-service flow), so the admin
 * can invite ahead of time. The org's `allowedDomain` gate applies here too — otherwise the
 * link step would reject the very record the admin just created.
 */
export async function addOrganizationContributor(
  organizationId: number,
  request: AddOrganizationContributorRequest,
): Promise<AdminContributorResponse> {
  const org = await requireOrganization(organizationId)
  const email = request.email.trim().toLowerCase()
  if (!EMAIL_PATTERN.test(email)) {
    throw new ValidationError(['email: must be a valid email address'])
  }
  assertEmailAllowed(org, email)

  const existing = await findContributorByEmail(org.id, email)
  if (existing && !existing.deletedAt) {
    throw new ConflictError(`A contributor with email ${email} already exists in organization ${org.slug}`)
  }

  const cognitoSub = await findCognitoSubByEmail(email)
  if (cognitoSub) {
    const linkedElsewhere = await findContributorByCognitoSub(cognitoSub)
    if (linkedElsewhere && !linkedElsewhere.deletedAt) {
      throw new ConflictError('That account is already linked to a contributor in another organization')
    }
  }

  const id = generateId()
  const now = new Date().toISOString()
  const item: ContributorItem = {
    pk: `${org.id}#CONTRIBUTOR`,
    sk: id.toString(),
    id,
    tenantId: org.id,
    name: request.name.trim(),
    email,
    avatarUrl: request.avatarUrl?.trim() || undefined,
    cognitoSub,
    createdAt: now,
    updatedAt: now,
  }
  return toAdminContributorResponse(await saveContributor(item))
}
