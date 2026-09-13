import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { APIGatewayProxyEventV2, Context } from 'aws-lambda'
import { ConflictError, ValidationError } from './errors'

// http.ts imports auth.ts, which builds the Cognito verifier at module load and needs a real
// user pool id — irrelevant to the pure helpers under test here.
vi.mock('./auth', () => ({ verifyRequest: vi.fn() }))
vi.mock('./repositories/contributor', () => ({ findContributorByCognitoSub: vi.fn() }))

import { verifyRequest } from './auth'
import { findContributorByCognitoSub } from './repositories/contributor'
import { MAX_PAGE_SIZE, pageable, withAuth } from './http'
import { EMAIL_PLACEHOLDER, log, setLogSink } from './logger'

describe('withAuth logging', () => {
  let lines: Record<string, any>[]
  const context = { awsRequestId: 'aws-req-1' } as Context
  const request = (headers: Record<string, string> = {}): APIGatewayProxyEventV2 =>
    ({
      rawPath: '/api/v1/playlists/42',
      rawQueryString: 'name=Alice%20Smith',
      headers: { authorization: 'Bearer t', ...headers },
      requestContext: { http: { method: 'GET' } },
    }) as unknown as APIGatewayProxyEventV2

  beforeEach(() => {
    lines = []
    setLogSink((line) => lines.push(JSON.parse(line)))
    vi.mocked(verifyRequest).mockResolvedValue({ sub: 'cognito-sub-1', email: 'alice@example.com' })
    vi.mocked(findContributorByCognitoSub).mockResolvedValue({ id: 7n, tenantId: 3 } as any)
  })

  afterEach(() => {
    setLogSink(undefined)
    vi.clearAllMocks()
  })

  it('scopes trace/request/tenant/user ids onto every line and writes a completion line', async () => {
    const handler = withAuth('authenticated-with-contributor', 200, async () => {
      log.info('inside handler')
      return { ok: true }
    })

    const result = await handler(
      request({ traceparent: '00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01', 'x-request-id': 'client-1' }),
      context,
    )

    expect(result.statusCode).toBe(200)
    expect(result.headers).toMatchObject({ 'Content-Type': 'application/json', 'x-request-id': 'client-1' })

    expect(lines.map((l) => l.message)).toEqual(['inside handler', 'HTTP request completed'])
    for (const line of lines) {
      expect(line).toMatchObject({
        trace: { id: '4bf92f3577b34da6a3ce929d0e0e4736' },
        http: { request: { id: 'client-1', method: 'GET' } },
        url: { path: '/api/v1/playlists/42' },
        tenant: { id: 3 },
        user: { id: '7' },
      })
      expect(line.span.id).toMatch(/^[0-9a-f]{16}$/)
    }
    const done = lines[1]
    expect(done.http.response.status_code).toBe(200)
    expect(Number(done.event.duration)).toBeGreaterThan(0)
    expect(typeof done.faas.coldstart).toBe('boolean')

    // nothing identifying: no Cognito email/sub, no query string, no bearer token
    const raw = JSON.stringify(lines)
    expect(raw).not.toContain('alice')
    expect(raw).not.toContain('Alice')
    expect(raw).not.toContain('cognito-sub-1')
    expect(raw).not.toContain('Bearer')
  })

  it('falls back to the Lambda request id and a generated trace id', async () => {
    const handler = withAuth('public', 200, async () => null)

    const result = await handler(request(), context)

    expect(result).toEqual({ statusCode: 200, headers: { 'x-request-id': 'aws-req-1' } })
    expect(lines).toHaveLength(1)
    expect(lines[0].http.request.id).toBe('aws-req-1')
    expect(lines[0].trace.id).toMatch(/^[0-9a-f]{32}$/)
    expect(lines[0]).not.toHaveProperty('tenant')
  })

  it('logs expected client errors as a warning without a stack trace', async () => {
    const handler = withAuth('public', 200, async () => {
      throw new ConflictError('Playlist 42 is not OPEN')
    })

    const result = await handler(request(), context)

    expect(result.statusCode).toBe(409)
    expect(lines.map((l) => l.log.level)).toEqual(['WARN', 'INFO'])
    expect(lines[0].error).toEqual({ type: 'ConflictError', message: 'Playlist 42 is not OPEN' })
    expect(lines[1].http.response.status_code).toBe(409)
  })

  it('logs unexpected errors at ERROR with a masked stack trace and returns 500', async () => {
    const handler = withAuth('public', 200, async () => {
      throw new TypeError('cannot read bob@example.com')
    })

    const result = await handler(request(), context)

    expect(result.statusCode).toBe(500)
    expect(lines[0].log.level).toBe('ERROR')
    expect(lines[0].error.type).toBe('TypeError')
    expect(lines[0].error.message).toBe(`cannot read ${EMAIL_PLACEHOLDER}`)
    expect(lines[0].error.stack_trace).toContain('TypeError')
    expect(JSON.stringify(lines)).not.toContain('example.com')
  })

  it('does not leak one invocation\'s context into the next', async () => {
    const handler = withAuth('authenticated-with-contributor', 200, async () => null)
    await handler(request({ 'x-request-id': 'first' }), context)
    vi.mocked(findContributorByCognitoSub).mockResolvedValue({ id: 8n, tenantId: 4 } as any)
    await handler(request({ 'x-request-id': 'second' }), context)

    expect(lines[0]).toMatchObject({ http: { request: { id: 'first' } }, tenant: { id: 3 }, user: { id: '7' } })
    expect(lines[1]).toMatchObject({ http: { request: { id: 'second' } }, tenant: { id: 4 }, user: { id: '8' } })
  })
})

const event = (query: Record<string, string>): APIGatewayProxyEventV2 =>
  ({ queryStringParameters: query, rawPath: '/' }) as unknown as APIGatewayProxyEventV2

describe('pageable', () => {
  it('is unpaged when size is absent', () => {
    expect(pageable(event({}))).toBeUndefined()
  })

  it('parses page and size', () => {
    expect(pageable(event({ page: '2', size: '20' }))).toEqual({ page: 2, size: 20 })
  })

  it('defaults page to 0', () => {
    expect(pageable(event({ size: '20' }))).toEqual({ page: 0, size: 20 })
  })

  it('caps size at MAX_PAGE_SIZE', () => {
    expect(pageable(event({ size: '999999' }))?.size).toBe(MAX_PAGE_SIZE)
  })

  // Number('abc') is NaN, which previously slid through and silently produced an empty page.
  it.each<Record<string, string>>([
    { size: 'abc' },
    { size: '0' },
    { size: '-5' },
    { size: '1.5' },
    { size: '10', page: 'abc' },
    { size: '10', page: '-1' },
  ])('rejects %j as a validation error', (query) => {
    expect(() => pageable(event(query))).toThrow(ValidationError)
  })
})
