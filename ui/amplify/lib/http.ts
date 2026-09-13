import type { APIGatewayProxyEventV2, APIGatewayProxyStructuredResultV2, Context } from 'aws-lambda'
import { ADMIN_GROUP, verifyRequest } from './auth'
import { ConflictError, ForbiddenError, NotFoundError, NotLinkedError, UnauthorizedError, ValidationError } from './errors'
import { Fields, addLogContext, log, runWithLogContext } from './logger'
import { REQUEST_ID_HEADER, newSpanId, resolveRequestId, resolveTrace } from './tracing'
import { findContributorByCognitoSub } from './repositories/contributor'

/**
 * Port of BaseHandler.java's AuthMode. 'public': no token check. 'authenticated': valid token
 * required, no Contributor lookup (only the link function — a brand-new Cognito user may have
 * no Contributor yet). 'authenticated-with-contributor': valid token AND a linked Contributor —
 * the default for ~30 of the 34 functions. 'admin' (no Java counterpart): valid token whose
 * `cognito:groups` claim contains ADMIN_GROUP — cross-tenant, so no Contributor/tenant is
 * resolved; the admin-* functions take the organization explicitly.
 */
export type AuthMode = 'public' | 'authenticated' | 'authenticated-with-contributor' | 'admin'

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

  if (mode === 'admin') {
    if (!claims.groups.includes(ADMIN_GROUP)) {
      throw new ForbiddenError('Administrator group membership required')
    }
    // Admin actions have no Contributor id to attribute them to; the opaque Cognito subject is
    // the only audit handle (the same `user.id` = JWT `sub` convention `backend` uses).
    addLogContext({ [Fields.USER_ID]: claims.sub, [Fields.USER_ROLES]: ['admin'] })
    return ctx
  }

  if (mode === 'authenticated-with-contributor') {
    const contributor = await findContributorByCognitoSub(claims.sub)
    if (!contributor || contributor.deletedAt) {
      throw new NotLinkedError()
    }
    ctx.tenantId = contributor.tenantId
    ctx.contributorId = contributor.id
    // Opaque ids only — the Cognito email/sub stay out of the log context.
    addLogContext({ [Fields.TENANT_ID]: contributor.tenantId, [Fields.USER_ID]: contributor.id })
  }

  return ctx
}

/** Module state survives across invocations of a warm execution environment — that's the point. */
let coldStart = true

/**
 * Wraps a handler body with auth resolution + the same error->status mapping BaseHandler.java
 * uses, inside a request-scoped log context (trace/request ids, method, path, then tenant +
 * contributor once resolved) that every log line during the invocation inherits. Writes one
 * "HTTP request completed" line per invocation with status and duration, and echoes the
 * request id back in `X-Request-ID` so a client can quote it.
 */
export function withAuth(mode: AuthMode, successStatus: number, fn: Handler) {
  return async (event: APIGatewayProxyEventV2, context?: Context): Promise<APIGatewayProxyStructuredResultV2> => {
    const requestId = resolveRequestId(event.headers, context?.awsRequestId)
    const isColdStart = coldStart
    coldStart = false

    return runWithLogContext(
      {
        [Fields.TRACE_ID]: resolveTrace(event.headers).traceId,
        [Fields.SPAN_ID]: newSpanId(),
        [Fields.REQUEST_ID]: requestId,
        [Fields.REQUEST_METHOD]: event.requestContext?.http?.method,
        [Fields.URL_PATH]: event.rawPath,
      },
      async () => {
        const start = process.hrtime.bigint()
        const result = await handle(mode, successStatus, fn, event)
        log.info('HTTP request completed', {
          [Fields.RESPONSE_STATUS]: result.statusCode,
          [Fields.EVENT_DURATION]: process.hrtime.bigint() - start,
          [Fields.FAAS_COLDSTART]: isColdStart,
        })
        return { ...result, headers: { ...result.headers, [REQUEST_ID_HEADER]: requestId } }
      },
    )
  }
}

async function handle(
  mode: AuthMode,
  successStatus: number,
  fn: Handler,
  event: APIGatewayProxyEventV2,
): Promise<APIGatewayProxyStructuredResultV2> {
  try {
    const ctx = await authenticate(mode, event)
    const body = await fn(event, ctx)
    return respond(successStatus, body)
  } catch (e) {
    const rejected = (status: number, body: unknown) => {
      // Expected client errors: type + message, no stack trace.
      const err = e as Error
      log.warn('Request rejected', { 'error.type': err.name, 'error.message': err.message })
      return respond(status, body)
    }
    if (e instanceof ValidationError) return rejected(400, { errors: e.errors })
    if (e instanceof UnauthorizedError) return rejected(401, { error: e.message })
    if (e instanceof NotLinkedError) return rejected(403, { error: e.message })
    if (e instanceof ForbiddenError) return rejected(403, { error: e.message })
    if (e instanceof NotFoundError) return rejected(404, { error: e.message })
    if (e instanceof ConflictError) return rejected(409, { error: e.message })
    log.error('Unhandled error', e)
    return respond(500, { error: 'Internal server error' })
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
