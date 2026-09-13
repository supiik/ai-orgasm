import { ConflictError, NotFoundError, ValidationError } from '../errors'
import { generateId } from '../idGenerator'
import { findContributorByEmail, saveContributor, type ContributorItem } from '../repositories/contributor'
import { findOrganizationBySlug, type OrganizationItem } from '../repositories/organization'
import { createContributor, toContributorResponse, type ContributorResponse } from './contributor'

export interface RegisterContributorRequest {
  organizationSlug: string
  name: string
  email?: string
  avatarUrl?: string
}

/**
 * No-op when the organization has no `allowedDomain` configured (default: unrestricted).
 * Mirrors `RegistrationController.assertEmailAllowed` on the Java side.
 */
function assertEmailAllowed(org: OrganizationItem, email: string | undefined): void {
  if (!org.allowedDomain) return
  if (!email) {
    throw new ValidationError([`Email required: organization "${org.slug}" only accepts @${org.allowedDomain} addresses`])
  }
  const domain = email.slice(email.lastIndexOf('@') + 1)
  if (domain.toLowerCase() !== org.allowedDomain.toLowerCase()) {
    throw new ValidationError([`Email domain does not match organization "${org.slug}"'s allowed domain (@${org.allowedDomain})`])
  }
}

/**
 * Port of the Java `POST /api/v1/register` — resolves the org by slug, then creates a Contributor
 * under its tenant. **Not exposed as a Function URL.** It was, as `register-contributor`, until the
 * 2026-09-11 security review: an unauthenticated, unrate-limited write that nothing in the UI
 * called (Cognito sign-up goes through `linkContributor` below, which has a verified identity).
 * Kept — with its tests — as the parity reference; wire it back up only behind real abuse controls.
 */
export async function register(request: RegisterContributorRequest): Promise<ContributorResponse> {
  const org = await findOrganizationBySlug(request.organizationSlug)
  if (!org) throw new NotFoundError(`Organization not found: ${request.organizationSlug}`)
  assertEmailAllowed(org, request.email)

  return createContributor(org.id, { name: request.name, email: request.email, avatarUrl: request.avatarUrl })
}

export interface LinkContributorRequest {
  organizationSlug: string
  name: string
  avatarUrl?: string
}

/**
 * Links an already-authenticated Cognito identity to a Contributor: attaches to an existing
 * Contributor by email match within the resolved Organization if one exists, otherwise creates
 * a new one. Mirrors `register` (same org-by-slug resolution) but keys off an already-verified
 * cognitoSub/email pair instead of being fully public.
 */
export async function linkContributor(
  request: LinkContributorRequest,
  cognitoSub: string,
  email: string,
): Promise<ContributorResponse> {
  const org = await findOrganizationBySlug(request.organizationSlug)
  if (!org) throw new NotFoundError(`Organization not found: ${request.organizationSlug}`)
  assertEmailAllowed(org, email)

  const existing = await findContributorByEmail(org.id, email)
  const item = existing ? await attach(existing, cognitoSub) : await create(request, org.id, email, cognitoSub)
  return toContributorResponse(item)
}

async function attach(existing: ContributorItem, cognitoSub: string): Promise<ContributorItem> {
  if (existing.cognitoSub && existing.cognitoSub !== cognitoSub) {
    throw new ConflictError('Contributor is already linked to a different account')
  }
  existing.cognitoSub = cognitoSub
  existing.updatedAt = new Date().toISOString()
  return saveContributor(existing)
}

async function create(request: LinkContributorRequest, tenantId: number, email: string, cognitoSub: string): Promise<ContributorItem> {
  const id = generateId()
  const now = new Date().toISOString()
  const item: ContributorItem = {
    pk: `${tenantId}#CONTRIBUTOR`,
    sk: id.toString(),
    id,
    tenantId,
    name: request.name,
    email,
    avatarUrl: request.avatarUrl,
    cognitoSub,
    createdAt: now,
    updatedAt: now,
  }
  return saveContributor(item)
}
