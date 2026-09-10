import { fetchAuthSession } from 'aws-amplify/auth'

// Minimal client for the two new Cognito-auth endpoints — NOT a general-purpose lambda API
// client. lambda has no shared base path (each endpoint is its own Function URL), and
// migrating the other business endpoints' UI calls from `backend` to `lambda` is a separate,
// larger decision (see CLAUDE.md "Cognito auth for the Lambda API").
const LINK_URL = import.meta.env.VITE_LAMBDA_LINK_URL as string | undefined
const ME_URL = import.meta.env.VITE_LAMBDA_ME_URL as string | undefined

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
  if (!LINK_URL) throw new Error('VITE_LAMBDA_LINK_URL is not configured')
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
  if (!ME_URL) throw new Error('VITE_LAMBDA_ME_URL is not configured')
  const res = await authorizedFetch(ME_URL)
  if (res.status === 403 || res.status === 404) return null
  if (!res.ok) throw new Error(`Failed to load current contributor: ${res.status}`)
  return res.json()
}
