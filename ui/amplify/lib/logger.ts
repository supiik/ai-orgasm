import { AsyncLocalStorage } from 'node:async_hooks'

/**
 * Structured JSON logger for the Lambda functions — one Elastic Common Schema (ECS) object per
 * line, the same shape and field names `backend`'s Spring Boot ECS output uses, so both APIs
 * land in one index with one set of dashboards/alerts. CloudWatch Logs Insights parses the JSON
 * automatically (`filter http.response.status_code >= 500 | stats count() by url.path`), and any
 * OTel/Fluent Bit/Datadog forwarder can ship the lines on as-is.
 *
 * Request-scoped context (`runWithLogContext` / `addLogContext`) is the Node equivalent of the
 * SLF4J MDC: everything logged inside the callback — services, repositories — carries the
 * request's trace id, request id, tenant etc. without threading a logger through every call.
 * It rides on AsyncLocalStorage, so it survives `await`s and is per-invocation even though the
 * execution environment (and module state) is reused across invocations.
 *
 * Only opaque ids belong in the context. Emails, names, Cognito `email` claims, request bodies,
 * query strings (the list endpoints take a `name` filter) and Authorization headers are never
 * logged; `maskPii` scrubs any email that slips into a message or field value as a safety net.
 */

export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
export type LogFields = Record<string, unknown>

/** ECS field names shared with backend's `LogFields.java`. Dotted names are emitted nested. */
export const Fields = {
  TRACE_ID: 'trace.id',
  SPAN_ID: 'span.id',
  REQUEST_ID: 'http.request.id',
  REQUEST_METHOD: 'http.request.method',
  URL_PATH: 'url.path',
  RESPONSE_STATUS: 'http.response.status_code',
  /** Nanoseconds, per ECS. */
  EVENT_DURATION: 'event.duration',
  TENANT_ID: 'tenant.id',
  /** The Contributor id — never the Cognito email. */
  USER_ID: 'user.id',
  /** Only set on `admin`-mode requests (`['admin']`) — everyone else is a plain Contributor. */
  USER_ROLES: 'user.roles',
  FAAS_COLDSTART: 'faas.coldstart',
} as const

export const EMAIL_PLACEHOLDER = '[redacted-email]'
// Same permissive pattern as backend's PiiMasker: a false positive costs a placeholder, a false
// negative leaks an address.
const EMAIL = /[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+/g

export function maskPii(value: string): string {
  return value.includes('@') ? value.replace(EMAIL, EMAIL_PLACEHOLDER) : value
}

const LEVEL_ORDER: Record<LogLevel, number> = { DEBUG: 10, INFO: 20, WARN: 30, ERROR: 40 }

function threshold(): number {
  const configured = (process.env.LOG_LEVEL ?? 'INFO').toUpperCase() as LogLevel
  return LEVEL_ORDER[configured] ?? LEVEL_ORDER.INFO
}

const context = new AsyncLocalStorage<LogFields>()

/** Runs `fn` with `fields` merged onto whatever context is already active. */
export function runWithLogContext<T>(fields: LogFields, fn: () => T): T {
  return context.run({ ...context.getStore(), ...fields }, fn)
}

/** Adds fields to the active context (e.g. tenant/contributor once auth has resolved). No-op outside one. */
export function addLogContext(fields: LogFields): void {
  const store = context.getStore()
  if (store) Object.assign(store, fields)
}

export function getLogContext(): LogFields {
  return { ...context.getStore() }
}

/** Expands `{'http.request.id': 'x'}` into `{http: {request: {id: 'x'}}}`, sanitising string values. */
function nest(fields: LogFields): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [path, raw] of Object.entries(fields)) {
    if (raw === undefined || raw === null) continue
    const value = typeof raw === 'bigint' ? raw.toString() : typeof raw === 'string' ? maskPii(raw) : raw
    const parts = path.split('.')
    let node = out
    for (const part of parts.slice(0, -1)) {
      const next = node[part]
      node = (typeof next === 'object' && next !== null ? next : (node[part] = {})) as Record<string, unknown>
    }
    node[parts[parts.length - 1]] = value
  }
  return out
}

function errorFields(error: unknown): Record<string, unknown> {
  if (error instanceof Error) {
    return {
      type: error.name,
      message: maskPii(error.message),
      ...(error.stack ? { stack_trace: maskPii(error.stack) } : {}),
    }
  }
  return { type: typeof error, message: maskPii(String(error)) }
}

/** Written to stdout directly, not console.log: the Lambda Node runtime prefixes console output. */
let sink: (line: string) => void = (line) => {
  process.stdout.write(line + '\n')
}

/** Test seam. */
export function setLogSink(next: ((line: string) => void) | undefined): void {
  sink = next ?? ((line) => process.stdout.write(line + '\n'))
}

function emit(level: LogLevel, message: string, fields?: LogFields, error?: unknown): void {
  if (LEVEL_ORDER[level] < threshold()) return
  const line = {
    '@timestamp': new Date().toISOString(),
    log: { level, logger: 'orgasm-lambda' },
    message: maskPii(message),
    service: {
      name: process.env.AWS_LAMBDA_FUNCTION_NAME ?? 'orgasm-lambda',
      version: process.env.AWS_LAMBDA_FUNCTION_VERSION,
      environment: process.env.APP_ENV,
    },
    process: { pid: process.pid },
    ...nest({ ...context.getStore(), ...fields }),
    ...(error !== undefined ? { error: errorFields(error) } : {}),
    ecs: { version: '8.11' },
  }
  sink(JSON.stringify(line))
}

export const log = {
  debug: (message: string, fields?: LogFields) => emit('DEBUG', message, fields),
  info: (message: string, fields?: LogFields) => emit('INFO', message, fields),
  warn: (message: string, fields?: LogFields, error?: unknown) => emit('WARN', message, fields, error),
  error: (message: string, error?: unknown, fields?: LogFields) => emit('ERROR', message, fields, error),
}
