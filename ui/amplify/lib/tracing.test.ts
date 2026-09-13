import { afterEach, describe, expect, it } from 'vitest'
import { newSpanId, resolveRequestId, resolveTrace } from './tracing'

describe('resolveTrace', () => {
  afterEach(() => {
    delete process.env._X_AMZN_TRACE_ID
  })

  it('prefers a W3C traceparent header', () => {
    process.env._X_AMZN_TRACE_ID = 'Root=1-5e1b4151-5ac6c58f5ac6c58f5ac6c58f;Sampled=1'
    const trace = resolveTrace({
      TraceParent: '00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01',
      'x-amzn-trace-id': 'Root=1-5e1b4151-5ac6c58f5ac6c58f5ac6c58f',
    })
    expect(trace.traceId).toBe('4bf92f3577b34da6a3ce929d0e0e4736')
  })

  it('ignores a malformed traceparent', () => {
    expect(resolveTrace({ traceparent: 'garbage' }).traceId).toMatch(/^[0-9a-f]{32}$/)
  })

  it('flattens the X-Ray root id from the header to the 32-hex OTel form', () => {
    const trace = resolveTrace({ 'X-Amzn-Trace-Id': 'Root=1-5e1b4151-5ac6c58f5ac6c58f5ac6c58f;Parent=53995c3f42cd8ad8;Sampled=0' })
    expect(trace.traceId).toBe('5e1b41515ac6c58f5ac6c58f5ac6c58f')
  })

  it('falls back to the runtime X-Ray env var', () => {
    process.env._X_AMZN_TRACE_ID = 'Root=1-aaaaaaaa-bbbbbbbbbbbbbbbbbbbbbbbb;Sampled=0'
    expect(resolveTrace(undefined).traceId).toBe('aaaaaaaabbbbbbbbbbbbbbbbbbbbbbbb')
  })

  it('generates a fresh id when nothing is propagated', () => {
    const a = resolveTrace({}).traceId
    const b = resolveTrace({}).traceId
    expect(a).toMatch(/^[0-9a-f]{32}$/)
    expect(a).not.toBe(b)
  })
})

describe('newSpanId', () => {
  it('is 16 hex chars', () => {
    expect(newSpanId()).toMatch(/^[0-9a-f]{16}$/)
  })
})

describe('resolveRequestId', () => {
  it('echoes a well-formed caller id', () => {
    expect(resolveRequestId({ 'X-Request-ID': 'gw-1234.abc_XYZ-9' }, 'aws-1')).toBe('gw-1234.abc_XYZ-9')
  })

  it.each(['evil\n"injected": true', 'x'.repeat(65), '', 'has space'])('replaces unsafe id %j', (bad) => {
    expect(resolveRequestId({ 'x-request-id': bad }, 'aws-1')).toBe('aws-1')
  })

  it('uses the Lambda request id, else a UUID', () => {
    expect(resolveRequestId(undefined, 'aws-1')).toBe('aws-1')
    expect(resolveRequestId(undefined, undefined)).toMatch(/^[0-9a-f-]{36}$/)
  })
})
