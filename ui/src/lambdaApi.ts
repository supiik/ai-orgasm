import { fetchAuthSession } from 'aws-amplify/auth'
import outputs from '../amplify_outputs.json'

// Minimal client for the two new Cognito-auth endpoints — NOT a general-purpose lambda API
// client. lambda has no shared base path (each endpoint is its own Function URL), and
// migrating the other business endpoints' UI calls from `backend` to `lambda` is a separate,
// larger decision (see CLAUDE.md "Cognito auth for the Lambda API"). URLs come from
// amplify_outputs.json's custom.functionUrls, populated by ui/amplify/backend.ts's
// backend.addOutput(...) — see amplify.ts for why that file is committed with placeholders.
const functionUrls = (outputs as { custom?: { functionUrls?: Record<string, string> } }).custom?.functionUrls ?? {}
const LINK_URL = functionUrls['link-contributor']
const ME_URL = functionUrls['get-current-contributor']
const ORGANIZATIONS_URL = functionUrls['list-organizations']

export interface OrganizationResponse {
  id: number
  slug: string
  name: string
}

export interface LinkContributorRequest {
  organizationSlug: string
  name: string
  avatarUrl?: string
}

export interface LambdaContributorResponse {
  id: string
  name: string
  email?: string
  avatarUrl?: string
  version?: number
  createdAt?: string
  updatedAt?: string
}

async function authorizedFetch(url: string, init?: RequestInit): Promise<Response> {
  const session = await fetchAuthSession()
  const token = session.tokens?.idToken?.toString()
  return fetch(url, {
    ...init,
    headers: {
      ...init?.headers,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
}

export async function linkContributor(request: LinkContributorRequest): Promise<LambdaContributorResponse> {
  if (!LINK_URL) throw new Error('amplify_outputs.json custom.functionUrls["link-contributor"] is not configured')
  const res = await authorizedFetch(LINK_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!res.ok) {
    const body = await res.json().catch(() => ({}))
    throw new Error(body.error ?? `Failed to link contributor: ${res.status}`)
  }
  return res.json()
}

/** Returns null when the signed-in Cognito identity has no linked Contributor yet (403). */
export async function getCurrentContributor(): Promise<LambdaContributorResponse | null> {
  if (!ME_URL) throw new Error('amplify_outputs.json custom.functionUrls["get-current-contributor"] is not configured')
  const res = await authorizedFetch(ME_URL)
  if (res.status === 403 || res.status === 404) return null
  if (!res.ok) throw new Error(`Failed to load current contributor: ${res.status}`)
  return res.json()
}

/** Public endpoint — no auth needed, may be called before the user signs in. */
export async function listOrganizations(): Promise<OrganizationResponse[]> {
  if (!ORGANIZATIONS_URL) throw new Error('amplify_outputs.json custom.functionUrls["list-organizations"] is not configured')
  const res = await fetch(ORGANIZATIONS_URL)
  if (!res.ok) throw new Error(`Failed to load organizations: ${res.status}`)
  return res.json()
}
