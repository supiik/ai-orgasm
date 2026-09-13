import { randomBytes, randomUUID } from 'node:crypto'
import type { APIGatewayProxyEventV2 } from 'aws-lambda'

/**
 * Resolves the correlation ids for one invocation. Preference order mirrors how a request reaches
 * a function: an upstream service that already has a trace (W3C `traceparent`, which is also what
 * `backend`'s Micrometer Tracing produces and consumes), then the X-Ray id the Function URL /
 * runtime stamps on every request, then a fresh id. X-Ray's `1-<8 hex epoch>-<24 hex>` root is
 * flattened to the 32-hex form the OTel X-Ray propagator uses, so ids are one shape everywhere.
 */
export interface TraceContext {
  traceId: string
}

export const REQUEST_ID_HEADER = 'x-request-id'

const TRACEPARENT = /^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$/i
const XRAY_ROOT = /Root=1-([0-9a-f]{8})-([0-9a-f]{24})/i
/** An inbound X-Request-ID is caller-controlled; anything outside this shape is replaced, not trusted. */
const SAFE_REQUEST_ID = /^[A-Za-z0-9._-]{1,64}$/

function header(headers: APIGatewayProxyEventV2['headers'] | undefined, name: string): string | undefined {
  if (!headers) return undefined
  return Object.entries(headers).find(([k]) => k.toLowerCase() === name)?.[1]
}

export function resolveTrace(headers: APIGatewayProxyEventV2['headers'] | undefined): TraceContext {
  const traceparent = header(headers, 'traceparent')?.match(TRACEPARENT)
  if (traceparent) {
    return { traceId: traceparent[1].toLowerCase() }
  }
  const xray = (header(headers, 'x-amzn-trace-id') ?? process.env._X_AMZN_TRACE_ID ?? '').match(XRAY_ROOT)
  if (xray) {
    return { traceId: (xray[1] + xray[2]).toLowerCase() }
  }
  return { traceId: randomBytes(16).toString('hex') }
}

/** Fresh 16-hex span id for this invocation's own unit of work. */
export function newSpanId(): string {
  return randomBytes(8).toString('hex')
}

/** Caller's X-Request-ID when well-formed, else the Lambda request id, else a UUID. */
export function resolveRequestId(headers: APIGatewayProxyEventV2['headers'] | undefined, awsRequestId?: string): string {
  const supplied = header(headers, REQUEST_ID_HEADER)
  if (supplied && SAFE_REQUEST_ID.test(supplied)) return supplied
  return awsRequestId ?? randomUUID()
}
