import type { APIGatewayProxyEventV2, APIGatewayProxyStructuredResultV2 } from 'aws-lambda'
import { verifyRequest } from './auth'
import { ConflictError, NotFoundError, NotLinkedError, UnauthorizedError, ValidationError } from './errors'
import { findContributorByCognitoSub } from './repositories/contributor'

/**
 * Port of BaseHandler.java's AuthMode. 'public': no token check. 'authenticated': valid token
 * required, no Contributor lookup (only the link function — a brand-new Cognito user may have
 * no Contributor yet). 'authenticated-with-contributor': valid token AND a linked Contributor —
 * the default for ~30 of the 34 functions.
 */
export type AuthMode = 'public' | 'authenticated' | 'authenticated-with-contributor'

export interface AuthContext {
  cognitoSub?: string
  cognitoEmail?: string
  /** Only set for 'authenticated-with-contributor'. */
  tenantId?: number
  contributorId?: bigint
}

type Handler = (event: APIGatewayProxyEventV2, ctx: AuthContext) => Promise<unknown>

async function authenticate(mode: AuthMode, event: APIGatewayProxyEventV2): Promise<AuthContext> {
  if (mode === 'public') return {}

  const claims = await verifyRequest(event.headers)
  const ctx: AuthContext = { cognitoSub: claims.sub, cognitoEmail: claims.email }

  if (mode === 'authenticated-with-contributor') {
    const contributor = await findContributorByCognitoSub(claims.sub)
    if (!contributor || contributor.deletedAt) {
      throw new NotLinkedError()
    }
    ctx.tenantId = contributor.tenantId
    ctx.contributorId = contributor.id
  }

  return ctx
}

/** Wraps a handler body with auth resolution + the same error->status mapping BaseHandler.java uses. */
export function withAuth(mode: AuthMode, successStatus: number, fn: Handler) {
  return async (event: APIGatewayProxyEventV2): Promise<APIGatewayProxyStructuredResultV2> => {
    console.log(`${event.requestContext?.http?.method} ${event.rawPath}`)
    try {
      const ctx = await authenticate(mode, event)
      const body = await fn(event, ctx)
      return respond(successStatus, body)
    } catch (e) {
      if (e instanceof ValidationError) return respond(400, { errors: e.errors })
      if (e instanceof UnauthorizedError) return respond(401, { error: e.message })
      if (e instanceof NotLinkedError) return respond(403, { error: e.message })
      if (e instanceof NotFoundError) return respond(404, { error: e.message })
      if (e instanceof ConflictError) return respond(409, { error: e.message })
      console.error('Handler error', e)
      return respond(500, { error: 'Internal server error' })
    }
  }
}

export function respond(statusCode: number, body: unknown): APIGatewayProxyStructuredResultV2 {
  if (body === null || body === undefined) {
    return { statusCode }
  }
  return {
    statusCode,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body, (_key, value) => (typeof value === 'bigint' ? value.toString() : value)),
  }
}

/**
 * Function URLs have no route templating — {id} segments come out of rawPath manually.
 * fromEnd=0 is the last segment, 1 the second-to-last, etc. — e.g. for
 * /api/v1/playlists/{id}/open, the id is pathSegment(event, 1).
 */
export function pathSegment(event: APIGatewayProxyEventV2, fromEnd: number): string {
  const segments = event.rawPath.split('/')
  const index = segments.length - 1 - fromEnd
  const value = segments[index]
  if (index < 0 || !value) {
    throw new NotFoundError(`Path segment not found: ${event.rawPath}`)
  }
  return value
}

export function queryParam(event: APIGatewayProxyEventV2, key: string): string | undefined {
  return event.queryStringParameters?.[key]
}

export interface PageRequest {
  page: number
  size: number
}

/**
 * Upper bound on `size`, so one request can't ask for an arbitrarily large response body.
 * Note this does NOT bound the read cost: the repositories page through a whole partition before
 * slicing in memory, so `size` only caps what is serialized back. See CLAUDE.md "Known gaps".
 */
export const MAX_PAGE_SIZE = 100

/**
 * Builds a page request from page/size query params, or undefined ("unpaged") if size is absent.
 * Rejects non-numeric/negative input rather than letting `Number()` yield NaN and silently
 * produce an empty page.
 */
export function pageable(event: APIGatewayProxyEventV2): PageRequest | undefined {
  const sizeParam = queryParam(event, 'size')
  if (!sizeParam) return undefined

  const size = Number(sizeParam)
  if (!Number.isInteger(size) || size < 1) {
    throw new ValidationError(['size: must be a positive integer'])
  }
  const page = Number(queryParam(event, 'page') ?? '0')
  if (!Number.isInteger(page) || page < 0) {
    throw new ValidationError(['page: must be a non-negative integer'])
  }

  return { page, size: Math.min(size, MAX_PAGE_SIZE) }
}

/** Shapes a paged result to match the OpenAPI spec's Page schemas (content/totalElements/totalPages/number/size). */
export function pageBody<T>(content: T[], totalElements: number, request: PageRequest | undefined) {
  const size = request?.size ?? (content.length || 1)
  return {
    content,
    totalElements,
    totalPages: Math.ceil(totalElements / size) || 0,
    number: request?.page ?? 0,
    size,
  }
}

export async function parseBody<T>(event: APIGatewayProxyEventV2): Promise<T> {
  if (!event.body) {
    throw new ValidationError(['body: request body is required'])
  }
  try {
    return JSON.parse(event.body) as T
  } catch {
    throw new ValidationError(['body: invalid JSON'])
  }
}

/** Throws ValidationError listing every blank/missing field — mirrors @NotBlank-style checks. */
export function requireFields(fields: Record<string, unknown>): void {
  const errors = Object.entries(fields)
    .filter(([, value]) => value === undefined || value === null || (typeof value === 'string' && value.trim() === ''))
    .map(([name]) => `${name}: must not be blank`)
  if (errors.length > 0) {
    throw new ValidationError(errors)
  }
}
